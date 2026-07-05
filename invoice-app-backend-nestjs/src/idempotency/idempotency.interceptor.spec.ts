import { BadRequestException, CallHandler, ConflictException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Test, TestingModule } from '@nestjs/testing';
import { ExecutionContextHost } from '@nestjs/core/helpers/execution-context-host';
import { lastValueFrom, of, throwError } from 'rxjs';
import { Role } from '@prisma/client';
import { IdempotencyInterceptor, IdempotentHttpRequest, IdempotentHttpResponse } from './idempotency.interceptor';
import { Idempotent } from './idempotent.decorator';
import { IdempotencyService } from './idempotency.service';

class FakePaymentsController {
  @Idempotent()
  createPayment(): void {}

  listPayments(): void {}
}

interface MockIdempotencyService {
  buildKey: jest.Mock;
  claim: jest.Mock;
  getRecord: jest.Mock;
  storeResponse: jest.Mock;
  releaseClaim: jest.Mock;
}

interface RecordedResponse extends IdempotentHttpResponse {
  statusCalls: number[];
  headers: Record<string, string>;
}

const makeRequest = (overrides: Partial<IdempotentHttpRequest> = {}): IdempotentHttpRequest => ({
  method: 'POST',
  path: '/api/invoices/i-1/payments',
  headers: { 'idempotency-key': 'client-key-1' },
  user: { id: 'user-1', email: 'a@b.com', role: Role.USER },
  ...overrides,
});

const makeResponse = (): RecordedResponse => {
  const recorded: RecordedResponse = {
    statusCode: 201,
    statusCalls: [],
    headers: {},
    status(code: number) {
      this.statusCalls.push(code);
      this.statusCode = code;
      return this;
    },
    setHeader(name: string, value: string) {
      this.headers[name] = value;
      return this;
    },
  };
  return recorded;
};

const contextFor = (
  request: IdempotentHttpRequest,
  response: IdempotentHttpResponse,
  handler: (typeof FakePaymentsController.prototype)['createPayment']
) => {
  const context = new ExecutionContextHost([request, response], FakePaymentsController, handler);
  context.setType('http');
  return context;
};

const handlerReturning = (body: unknown): CallHandler => ({
  handle: () => of(body),
});

describe('IdempotencyInterceptor', () => {
  let interceptor: IdempotencyInterceptor;
  let idempotency: MockIdempotencyService;

  beforeEach(async () => {
    idempotency = {
      buildKey: jest.fn(
        (userId: string, method: string, path: string, key: string) => `idem:${userId}:${method}:${path}:${key}`
      ),
      claim: jest.fn(),
      getRecord: jest.fn(),
      storeResponse: jest.fn().mockResolvedValue(undefined),
      releaseClaim: jest.fn().mockResolvedValue(undefined),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [IdempotencyInterceptor, Reflector, { provide: IdempotencyService, useValue: idempotency }],
    }).compile();

    interceptor = module.get(IdempotencyInterceptor);
  });

  it('passes undecorated routes through untouched', async () => {
    const ctx = contextFor(
        makeRequest({ headers: {} }),
        makeResponse(),
        FakePaymentsController.prototype.listPayments
    );

    const result = await interceptor.intercept(ctx, handlerReturning('ok'));

    await expect(lastValueFrom(result)).resolves.toBe('ok');
    expect(idempotency.claim).not.toHaveBeenCalled();
  });

  it('rejects a decorated route without the header (400), handler never runs', async () => {
    const handle = jest.fn();
    const ctx = contextFor(
      makeRequest({ headers: {} }),
      makeResponse(),
      FakePaymentsController.prototype.createPayment
    );

    await expect(interceptor.intercept(ctx, { handle })).rejects.toBeInstanceOf(BadRequestException);
    expect(handle).not.toHaveBeenCalled();
    expect(idempotency.claim).not.toHaveBeenCalled();
  });

  it('claims a user-scoped key built from the verified identity and route', async () => {
    idempotency.claim.mockResolvedValue(true);
    const ctx = contextFor(makeRequest(), makeResponse(), FakePaymentsController.prototype.createPayment);

    const result = await interceptor.intercept(ctx, handlerReturning({ id: 'p1' }));
    await lastValueFrom(result);

    expect(idempotency.claim).toHaveBeenCalledWith('idem:user-1:POST:/api/invoices/i-1/payments:client-key-1');
  });

  it('on a fresh claim: runs the handler, stores {status, body}, then emits', async () => {
    idempotency.claim.mockResolvedValue(true);
    const response = makeResponse();
    const ctx = contextFor(makeRequest(), response, FakePaymentsController.prototype.createPayment);

    const result = await interceptor.intercept(ctx, handlerReturning({ id: 'p1' }));

    await expect(lastValueFrom(result)).resolves.toEqual({ id: 'p1' });
    expect(idempotency.storeResponse).toHaveBeenCalledWith(
      'idem:user-1:POST:/api/invoices/i-1/payments:client-key-1',
      201,
      { id: 'p1' }
    );
    expect(idempotency.releaseClaim).not.toHaveBeenCalled();
  });

  it('replays a completed duplicate: stored status, replay header, stored body, no handler', async () => {
    idempotency.claim.mockResolvedValue(false);
    idempotency.getRecord.mockResolvedValue({
      state: 'completed',
      status: 201,
      body: { id: 'p1' },
    });
    const handle = jest.fn();
    const response = makeResponse();
    const ctx = contextFor(makeRequest(), response, FakePaymentsController.prototype.createPayment);

    const result = await interceptor.intercept(ctx, { handle });

    await expect(lastValueFrom(result)).resolves.toEqual({ id: 'p1' });
    expect(response.statusCalls).toEqual([201]);
    expect(response.headers['Idempotency-Replayed']).toBe('true');
    expect(handle).not.toHaveBeenCalled();
    expect(idempotency.storeResponse).not.toHaveBeenCalled();
  });

  it('returns 409 for a duplicate that is still in progress', async () => {
    idempotency.claim.mockResolvedValue(false);
    idempotency.getRecord.mockResolvedValue({ state: 'in-progress' });
    const handle = jest.fn();
    const ctx = contextFor(makeRequest(), makeResponse(), FakePaymentsController.prototype.createPayment);

    await expect(interceptor.intercept(ctx, { handle })).rejects.toBeInstanceOf(ConflictException);
    expect(handle).not.toHaveBeenCalled();
  });

  it('returns 409 when the claim vanished between SET and GET (expired edge)', async () => {
    idempotency.claim.mockResolvedValue(false);
    idempotency.getRecord.mockResolvedValue(null);
    const ctx = contextFor(makeRequest(), makeResponse(), FakePaymentsController.prototype.createPayment);

    await expect(interceptor.intercept(ctx, { handle: jest.fn() })).rejects.toBeInstanceOf(ConflictException);
  });

  it('on handler failure: releases the claim, propagates the error, stores nothing', async () => {
    idempotency.claim.mockResolvedValue(true);
    const boom = new Error('overpayment rejected');
    const failingHandler: CallHandler = {
      handle: () => throwError(() => boom),
    };
    const ctx = contextFor(makeRequest(), makeResponse(), FakePaymentsController.prototype.createPayment);

    const result = await interceptor.intercept(ctx, failingHandler);

    await expect(lastValueFrom(result)).rejects.toBe(boom);
    expect(idempotency.releaseClaim).toHaveBeenCalledWith('idem:user-1:POST:/api/invoices/i-1/payments:client-key-1');
    expect(idempotency.storeResponse).not.toHaveBeenCalled();
  });

  it('scopes unauthenticated requests under anon', async () => {
    idempotency.claim.mockResolvedValue(true);
    const ctx = contextFor(
      makeRequest({ user: undefined }),
      makeResponse(),
      FakePaymentsController.prototype.createPayment
    );

    const result = await interceptor.intercept(ctx, handlerReturning('ok'));
    await lastValueFrom(result);

    expect(idempotency.claim).toHaveBeenCalledWith('idem:anon:POST:/api/invoices/i-1/payments:client-key-1');
  });
});
