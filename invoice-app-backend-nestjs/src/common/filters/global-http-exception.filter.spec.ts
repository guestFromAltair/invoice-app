import {
  ArgumentsHost,
  BadRequestException,
  HttpException,
  HttpStatus,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { HttpAdapterHost, AbstractHttpAdapter } from '@nestjs/core';
import { Prisma } from '@prisma/client';
import { GlobalHttpExceptionFilter } from './global-http-exception.filter';

describe('GlobalHttpExceptionFilter', () => {
  let filter: GlobalHttpExceptionFilter;
  let mockReply: jest.Mock;
  let mockGetRequestUrl: jest.Mock;

  const mockHttpArgumentsHost = {
    getRequest: jest.fn().mockReturnValue({ method: 'POST', url: '/api/auth/register' }),
    getResponse: jest.fn().mockReturnValue({}),
    getNext: jest.fn(),
  };

  const mockArgumentsHost: ArgumentsHost = {
    switchToHttp: () => mockHttpArgumentsHost,
    getType: jest.fn(),
    getArgs: jest.fn(),
    getArgByIndex: jest.fn(),
    switchToRpc: jest.fn(),
    switchToWs: jest.fn(),
  };

  beforeEach(() => {
    mockReply = jest.fn();
    mockGetRequestUrl = jest.fn().mockReturnValue('/api/auth/register');

    jest.spyOn(Logger.prototype, 'error').mockImplementation(() => {});
    jest.spyOn(Logger, 'error').mockImplementation(() => {});

    const mockAdapterHost = new HttpAdapterHost();
    const mockHttpAdapter: Partial<AbstractHttpAdapter> = {
      reply: mockReply,
      getRequestUrl: mockGetRequestUrl,
    };
    mockAdapterHost.httpAdapter = mockHttpAdapter as AbstractHttpAdapter;

    filter = new GlobalHttpExceptionFilter(mockAdapterHost);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  const getLatestReplyData = () => {
    const allCalls = mockReply.mock.calls;

    if (allCalls.length === 0) {
      throw new Error('The httpAdapter.reply method was never called.');
    }

    const latestCallArgs = allCalls[allCalls.length - 1];
    const [, responseBody, statusCode] = latestCallArgs;

    return {
      body: responseBody as Record<string, any>,
      status: statusCode as number,
    };
  };

  it('preserves a native HttpException status and message', () => {
    const exception = new UnauthorizedException('Invalid credentials');
    filter.catch(exception, mockArgumentsHost);

    const { body, status } = getLatestReplyData();

    expect(status).toBe(HttpStatus.UNAUTHORIZED);
    expect(body.message).toBe('Invalid credentials');
    expect(body.path).toBe('/api/auth/register');
    expect(body.timestamp).toBeDefined();
  });

  it('handles a raw string primitive response payload inside native HttpExceptions cleanly', () => {
    const stringException = new HttpException('Custom Opaque Exception Message', HttpStatus.BAD_REQUEST);

    filter.catch(stringException, mockArgumentsHost);

    const { body, status } = getLatestReplyData();

    expect(status).toBe(HttpStatus.BAD_REQUEST);
    expect(body.message).toBe('Custom Opaque Exception Message');
  });

  it('preserves the ValidationPipe message ARRAY untouched', () => {
    const validationError = new BadRequestException({
      statusCode: 400,
      message: ['email must be an email', 'password is too short'],
      error: 'Bad Request',
    });

    filter.catch(validationError, mockArgumentsHost);

    const { body, status } = getLatestReplyData();

    expect(status).toBe(HttpStatus.BAD_REQUEST);
    expect(body.message).toEqual(['email must be an email', 'password is too short']);
  });

  it('maps Prisma P2002 (unique violation) to 409 Conflict', () => {
    const prismaError = new Prisma.PrismaClientKnownRequestError('Unique constraint failed', {
      code: 'P2002',
      clientVersion: '5.22.0',
      meta: { target: ['email'] },
    });

    filter.catch(prismaError, mockArgumentsHost);

    const { body, status } = getLatestReplyData();

    expect(status).toBe(HttpStatus.CONFLICT);
    expect(body.message).toContain('email');
  });

  it('maps Prisma P2025 (not found) to 404', () => {
    const prismaError = new Prisma.PrismaClientKnownRequestError('Not found', {
      code: 'P2025',
      clientVersion: '5.22.0',
    });

    filter.catch(prismaError, mockArgumentsHost);

    const { status } = getLatestReplyData();

    expect(status).toBe(HttpStatus.NOT_FOUND);
  });

  it('maps an unknown error to an opaque 500 without leaking internals', () => {
    const rawError = new Error('secret stack trace details');
    filter.catch(rawError, mockArgumentsHost);

    const { body, status } = getLatestReplyData();

    expect(status).toBe(HttpStatus.INTERNAL_SERVER_ERROR);
    expect(body.message).toBe('Internal server error');
    expect(JSON.stringify(body)).not.toContain('secret stack trace');
  });

  it('handles non-Error objects thrown gracefully inside internal logs', () => {
    const rawStringException = 'Unexpected critical string throw';
    filter.catch(rawStringException, mockArgumentsHost);

    const { body, status } = getLatestReplyData();
    expect(status).toBe(HttpStatus.INTERNAL_SERVER_ERROR);
    expect(body.message).toBe('Internal server error');
  });

  it('maps Prisma P2003 (foreign key constraint) to 400 Bad Request', () => {
    const prismaError = new Prisma.PrismaClientKnownRequestError('Foreign key constraint failed', {
      code: 'P2003',
      clientVersion: '5.22.0',
    });

    filter.catch(prismaError, mockArgumentsHost);

    const { body, status } = getLatestReplyData();
    expect(status).toBe(HttpStatus.BAD_REQUEST);
    expect(body.message).toContain('related record constraint');
  });

  it('falls back to a generic unique message when Prisma P2002 meta target is missing', () => {
    const prismaError = new Prisma.PrismaClientKnownRequestError('Unique constraint failed', {
      code: 'P2002',
      clientVersion: '5.22.0',
      meta: {},
    });

    filter.catch(prismaError, mockArgumentsHost);

    const { body, status } = getLatestReplyData();
    expect(status).toBe(HttpStatus.CONFLICT);
    expect(body.message).toBe('Unique constraint violation');
  });

  it('maps an unhandled Prisma error code to an internal server error status', () => {
    const unmappedPrismaError = new Prisma.PrismaClientKnownRequestError('Unknown internal DB error', {
      code: 'P9999' as any,
      clientVersion: '5.22.0',
    });

    filter.catch(unmappedPrismaError, mockArgumentsHost);

    const { body, status } = getLatestReplyData();
    expect(status).toBe(HttpStatus.INTERNAL_SERVER_ERROR);
    expect(body.message).toBe('A database error occurred');
  });

  it('falls back to native exception properties when object payloads lack message or error keys', () => {
    const barrenException = new HttpException(
      { strangePayloadKey: 'no message or error structural data here' },
      HttpStatus.BAD_REQUEST
    );

    filter.catch(barrenException, mockArgumentsHost);

    const { body, status } = getLatestReplyData();

    expect(status).toBe(HttpStatus.BAD_REQUEST);
    expect(body.message).toBe('Http Exception'); // Native exception.message default fallback
    expect(body.error).toBe('HttpException'); // Native exception.name default fallback
  });
});
