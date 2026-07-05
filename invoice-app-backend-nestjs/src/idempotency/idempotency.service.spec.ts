import { Test, TestingModule } from '@nestjs/testing';
import { CLAIM_TTL_MS, IdempotencyService, RESPONSE_TTL_MS } from './idempotency.service';
import { RedisService } from '../redis/redis.service';

interface MockRedisService {
  setIfNotExists: jest.Mock;
  setWithTtl: jest.Mock;
  get: jest.Mock;
  delete: jest.Mock;
}

describe('IdempotencyService', () => {
  let service: IdempotencyService;
  let redis: MockRedisService;

  beforeEach(async () => {
    redis = {
      setIfNotExists: jest.fn(),
      setWithTtl: jest.fn(),
      get: jest.fn(),
      delete: jest.fn(),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [IdempotencyService, { provide: RedisService, useValue: redis }],
    }).compile();

    service = module.get(IdempotencyService);
  });

  it('builds user-scoped keys (tenant isolation lives in the key itself)', () => {
    expect(service.buildKey('user-1', 'POST', '/api/invoices/i-1/payments', 'abc')).toBe(
      'idem:user-1:POST:/api/invoices/i-1/payments:abc'
    );
  });

  describe('claim', () => {
    it('writes an in-progress marker atomically with the claim TTL', async () => {
      redis.setIfNotExists.mockResolvedValue(true);

      await expect(service.claim('idem:k')).resolves.toBe(true);

      expect(redis.setIfNotExists).toHaveBeenCalledWith(
        'idem:k',
        JSON.stringify({ state: 'in-progress' }),
        CLAIM_TTL_MS
      );
    });

    it('returns false when the key is already held', async () => {
      redis.setIfNotExists.mockResolvedValue(false);

      await expect(service.claim('idem:k')).resolves.toBe(false);
    });
  });

  describe('getRecord', () => {
    it('parses a completed record', async () => {
      redis.get.mockResolvedValue(JSON.stringify({ state: 'completed', status: 201, body: { id: 'p1' } }));

      await expect(service.getRecord('idem:k')).resolves.toEqual({
        state: 'completed',
        status: 201,
        body: { id: 'p1' },
      });
    });

    it('returns null for a missing key', async () => {
      redis.get.mockResolvedValue(null);

      await expect(service.getRecord('idem:k')).resolves.toBeNull();
    });

    it('returns null (not a crash) for a corrupt value', async () => {
      redis.get.mockResolvedValue('not-json{{{');

      await expect(service.getRecord('idem:k')).resolves.toBeNull();
    });
  });

  it('stores the completed response with the 24h replay TTL', async () => {
    redis.setWithTtl.mockResolvedValue(undefined);

    await service.storeResponse('idem:k', 201, { id: 'p1' });

    expect(redis.setWithTtl).toHaveBeenCalledWith(
      'idem:k',
      JSON.stringify({ state: 'completed', status: 201, body: { id: 'p1' } }),
      RESPONSE_TTL_MS
    );
  });

  it('releases a claim by deleting the key', async () => {
    redis.delete.mockResolvedValue(undefined);

    await service.releaseClaim('idem:k');

    expect(redis.delete).toHaveBeenCalledWith('idem:k');
  });
});
