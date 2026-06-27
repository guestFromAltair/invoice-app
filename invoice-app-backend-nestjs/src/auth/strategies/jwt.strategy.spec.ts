import { Role } from '@prisma/client';
import { JwtStrategy } from './jwt.strategy';
import { JwtPayload } from '../types/authenticated-user.type';

describe('JwtStrategy', () => {
  let strategy: JwtStrategy;

  beforeEach(() => {
    strategy = new JwtStrategy({ secret: 'test-secret', expiresIn: 3600 });
  });

  it('maps the JWT payload to a sanitized AuthenticatedUser', () => {
    const payload: JwtPayload = {
      sub: 'user-uuid-1',
      email: 'demo@invoiceapp.com',
      role: Role.USER,
    };

    const result = strategy.validate(payload);

    expect(result).toEqual({
      id: 'user-uuid-1',
      email: 'demo@invoiceapp.com',
      role: Role.USER,
    });
  });

  it('translates the sub claim into the id field', () => {
    const payload: JwtPayload = {
      sub: 'abc-123',
      email: 'admin@invoiceapp.com',
      role: Role.ADMIN,
    };

    expect(strategy.validate(payload).id).toBe('abc-123');
  });
});