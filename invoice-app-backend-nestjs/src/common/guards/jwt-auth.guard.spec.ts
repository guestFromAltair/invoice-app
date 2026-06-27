import { ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { AuthGuard } from '@nestjs/passport';
import { JwtAuthGuard } from './jwt-auth.guard';

describe('JwtAuthGuard', () => {
  let guard: JwtAuthGuard;
  let mockReflector: Reflector;

  const mockExecutionContext: ExecutionContext = {
    getHandler: jest.fn().mockReturnValue({}),
    getClass: jest.fn().mockReturnValue({}),
    getType: jest.fn(),
    switchToHttp: jest.fn(),
    switchToRpc: jest.fn(),
    switchToWs: jest.fn(),
    getArgs: jest.fn(),
    getArgByIndex: jest.fn(),
  };

  beforeEach(() => {
    mockReflector = Object.create(Reflector.prototype);
    mockReflector.getAllAndOverride = jest.fn();

    guard = new JwtAuthGuard(mockReflector);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('allows the request without authentication when the route is @Public()', () => {
    jest.spyOn(mockReflector, 'getAllAndOverride').mockReturnValue(true);

    expect(guard.canActivate(mockExecutionContext)).toBe(true);
  });

  it('delegates to the passport AuthGuard when the route is not public', () => {
    jest.spyOn(mockReflector, 'getAllAndOverride').mockReturnValue(false);

    const BaseAuthGuard = AuthGuard('jwt');
    const superCanActivate = jest.spyOn(BaseAuthGuard.prototype, 'canActivate').mockReturnValue(true);

    const result = guard.canActivate(mockExecutionContext);

    expect(superCanActivate).toHaveBeenCalledWith(mockExecutionContext);
    expect(result).toBe(true);
  });
});
