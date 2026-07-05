import { Injectable } from '@nestjs/common';
import { RedisService } from '../redis/redis.service';

export interface InProgressRecord {
  state: 'in-progress';
}

export interface CompletedRecord {
  state: 'completed';
  status: number;
  body: unknown;
}

export type IdempotencyRecord = InProgressRecord | CompletedRecord;

export const CLAIM_TTL_MS = 60_000;
export const RESPONSE_TTL_MS = 24 * 60 * 60 * 1000;

@Injectable()
export class IdempotencyService {
  constructor(private readonly redis: RedisService) {}

  buildKey(userId: string, method: string, path: string, clientKey: string): string {
    return `idem:${userId}:${method}:${path}:${clientKey}`;
  }

  claim(key: string): Promise<boolean> {
    const marker: InProgressRecord = { state: 'in-progress' };
    return this.redis.setIfNotExists(key, JSON.stringify(marker), CLAIM_TTL_MS);
  }

  async getRecord(key: string): Promise<IdempotencyRecord | null> {
    const raw = await this.redis.get(key);
    if (raw === null) {
      return null;
    }

    try {
      return JSON.parse(raw) as IdempotencyRecord;
    } catch {
      return null;
    }
  }

  storeResponse(key: string, status: number, body: unknown): Promise<void> {
    const record: CompletedRecord = { state: 'completed', status, body };
    return this.redis.setWithTtl(key, JSON.stringify(record), RESPONSE_TTL_MS);
  }

  releaseClaim(key: string): Promise<void> {
    return this.redis.delete(key);
  }
}
