import {
  BadRequestException,
  CallHandler,
  ConflictException,
  ExecutionContext,
  Injectable,
  NestInterceptor,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { from, Observable, of, throwError } from 'rxjs';
import { catchError, concatMap, map, mergeMap } from 'rxjs/operators';
import { IS_IDEMPOTENT_KEY } from './idempotent.decorator';
import { IdempotencyService } from './idempotency.service';
import { AuthenticatedUser } from '../auth/types/authenticated-user.type';

export interface IdempotentHttpRequest {
  method: string;
  path: string;
  headers: Record<string, string | string[] | undefined>;
  user?: AuthenticatedUser;
}

export interface IdempotentHttpResponse {
  statusCode: number;
  status(code: number): this;
  setHeader(name: string, value: string): this;
}

export const IDEMPOTENCY_HEADER = 'idempotency-key';
export const REPLAYED_HEADER = 'Idempotency-Replayed';

@Injectable()
export class IdempotencyInterceptor implements NestInterceptor {
  constructor(
    private readonly reflector: Reflector,
    private readonly idempotencyService: IdempotencyService
  ) {}

  async intercept(context: ExecutionContext, next: CallHandler): Promise<Observable<unknown>> {
    const isIdempotent = this.reflector.getAllAndOverride<boolean>(IS_IDEMPOTENT_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    if (!isIdempotent) {
      return next.handle();
    }

    const request = context.switchToHttp().getRequest<IdempotentHttpRequest>();
    const response = context.switchToHttp().getResponse<IdempotentHttpResponse>();

    const clientKey = this.extractKey(request);
    const userId = request.user?.id ?? 'anon';
    const key = this.idempotencyService.buildKey(userId, request.method, request.path, clientKey);

    const claimed = await this.idempotencyService.claim(key);

    if (!claimed) {
      return this.handleDuplicate(key, response);
    }

    return next.handle().pipe(
      concatMap((body) =>
        from(this.idempotencyService.storeResponse(key, response.statusCode, body)).pipe(map(() => body))
      ),
      catchError((error) =>
        from(this.idempotencyService.releaseClaim(key)).pipe(mergeMap(() => throwError(() => error)))
      )
    );
  }

  private extractKey(request: IdempotentHttpRequest): string {
    const raw = request.headers[IDEMPOTENCY_HEADER];
    const value = Array.isArray(raw) ? raw[0] : raw;

    if (!value || value.trim().length === 0) {
      throw new BadRequestException('Idempotency-Key header is required for this operation');
    }
    if (value.length > 255) {
      throw new BadRequestException('Idempotency-Key must be at most 255 characters');
    }

    return value;
  }

  private async handleDuplicate(key: string, response: IdempotentHttpResponse): Promise<Observable<unknown>> {
    const record = await this.idempotencyService.getRecord(key);

    if (record?.state === 'completed') {
      response.status(record.status);
      response.setHeader(REPLAYED_HEADER, 'true');
      return of(record.body);
    }

    throw new ConflictException(
      'A request with this Idempotency-Key is already being processed. ' + 'Retry shortly to receive its result.'
    );
  }
}
