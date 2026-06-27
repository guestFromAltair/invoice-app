import { ExecutionContext, ForbiddenException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Role } from '@prisma/client';
import { RolesGuard } from './roles.guard';
import { AuthenticatedUser } from '../../auth/types/authenticated-user.type';

describe('RolesGuard', () => {
  let guard: RolesGuard;
  let mockReflector: Reflector;

  const createContextWithUser = (user: AuthenticatedUser | undefined): ExecutionContext => {
    const mockHttpArgumentsHost = {
      getRequest: jest.fn().mockReturnValue({ user }),
      getResponse: jest.fn().mockReturnValue({}),
      getNext: jest.fn(),
    };

    return {
      getHandler: jest.fn().mockReturnValue({}),
      getClass: jest.fn().mockReturnValue({}),
      switchToHttp: () => mockHttpArgumentsHost,
      getType: jest.fn(),
      getArgs: jest.fn(),
      getArgByIndex: jest.fn(),
      switchToRpc: jest.fn(),
      switchToWs: jest.fn(),
    };
  };

  beforeEach(() => {
    mockReflector = Object.create(Reflector.prototype);
    mockReflector.getAllAndOverride = jest.fn();

    guard = new RolesGuard(mockReflector);
  });

  it('allows the request when no @Roles() metadata is present', () => {
    jest.spyOn(mockReflector, 'getAllAndOverride').mockReturnValue(undefined);

    const ctx = createContextWithUser({
      id: '1',
      email: 'a@b.com',
      role: Role.USER,
    });

    expect(guard.canActivate(ctx)).toBe(true);
  });

  it('allows the request when the user holds a required role', () => {
    jest.spyOn(mockReflector, 'getAllAndOverride').mockReturnValue([Role.ADMIN]);

    const ctx = createContextWithUser({
      id: '1',
      email: 'admin@b.com',
      role: Role.ADMIN,
    });

    expect(guard.canActivate(ctx)).toBe(true);
  });

  it('throws ForbiddenException when the user lacks the required role', () => {
    jest.spyOn(mockReflector, 'getAllAndOverride').mockReturnValue([Role.ADMIN]);

    const ctx = createContextWithUser({
      id: '1',
      email: 'user@b.com',
      role: Role.USER,
    });

    expect(() => guard.canActivate(ctx)).toThrow(ForbiddenException);
  });

  it('throws ForbiddenException when no user is present on the request', () => {
    jest.spyOn(mockReflector, 'getAllAndOverride').mockReturnValue([Role.USER]);

    const ctx = createContextWithUser(undefined);

    expect(() => guard.canActivate(ctx)).toThrow(ForbiddenException);
  });

  it('allows the request when an empty roles array is explicitly specified', () => {
    jest.spyOn(mockReflector, 'getAllAndOverride').mockReturnValue([]);

    const ctx = createContextWithUser({
      id: '1',
      email: 'a@b.com',
      role: Role.USER,
    });

    expect(guard.canActivate(ctx)).toBe(true);
  });
});
