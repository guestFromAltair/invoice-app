import { Test, TestingModule } from '@nestjs/testing';
import { ConflictException, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Prisma, Role, User } from '@prisma/client';
import { AuthService } from './auth.service';
import { UsersService } from '../users/users.service';
import { HashingService } from './hashing/hashing.service';
import jwtConfig from '../config/jwt.config';

describe('AuthService', () => {
  let service: AuthService;

  let usersService: jest.Mocked<UsersService>;
  let hashingService: jest.Mocked<HashingService>;
  let jwtService: jest.Mocked<JwtService>;

  const fakeUser: User = {
    id: 'user-uuid-1',
    email: 'demo@invoiceapp.com',
    password: 'hashed-password',
    role: Role.USER,
    version: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        {
          provide: UsersService,
          useValue: { findByEmail: jest.fn(), create: jest.fn() },
        },
        {
          provide: HashingService,
          useValue: { hash: jest.fn(), compare: jest.fn() },
        },
        {
          provide: JwtService,
          useValue: { signAsync: jest.fn() },
        },
        {
          provide: jwtConfig.KEY,
          useValue: { secret: 'test-secret', expiresIn: 3600 },
        },
      ],
    }).compile();

    service = module.get(AuthService);

    usersService = module.get(UsersService) as jest.Mocked<UsersService>;
    hashingService = module.get(HashingService) as jest.Mocked<HashingService>;
    jwtService = module.get(JwtService) as jest.Mocked<JwtService>;
  });

  describe('register', () => {
    it('hashes the password, persists the user, and returns a token', async () => {
      hashingService.hash.mockResolvedValue('hashed-password');
      usersService.create.mockResolvedValue(fakeUser);
      jwtService.signAsync.mockResolvedValue('signed.jwt.token');

      const result = await service.register({
        email: 'demo@invoiceapp.com',
        password: 'password123',
      });

      expect(hashingService.hash).toHaveBeenCalledWith('password123');
      expect(usersService.create).toHaveBeenCalledWith({
        email: 'demo@invoiceapp.com',
        password: 'hashed-password',
      });
      expect(result).toEqual({ accessToken: 'signed.jwt.token' });
    });

    it('signs a payload using the subject (sub) claim for the user id', async () => {
      hashingService.hash.mockResolvedValue('hashed-password');
      usersService.create.mockResolvedValue(fakeUser);
      jwtService.signAsync.mockResolvedValue('signed.jwt.token');

      await service.register({
        email: 'demo@invoiceapp.com',
        password: 'password123',
      });

      expect(jwtService.signAsync).toHaveBeenCalledWith(
        { sub: fakeUser.id, email: fakeUser.email, role: fakeUser.role },
        { secret: 'test-secret', expiresIn: 3600 }
      );
    });

    it('throws ConflictException when the email already exists (Prisma P2002)', async () => {
      hashingService.hash.mockResolvedValue('hashed-password');
      usersService.create.mockRejectedValue(
        new Prisma.PrismaClientKnownRequestError('Unique constraint', {
          code: 'P2002',
          clientVersion: '5.22.0',
        })
      );

      await expect(
        service.register({
          email: 'demo@invoiceapp.com',
          password: 'password123',
        })
      ).rejects.toBeInstanceOf(ConflictException);
    });

    it('rethrows unknown errors unchanged', async () => {
      hashingService.hash.mockResolvedValue('hashed-password');
      const boom = new Error('database is on fire');
      usersService.create.mockRejectedValue(boom);

      await expect(
        service.register({
          email: 'demo@invoiceapp.com',
          password: 'password123',
        })
      ).rejects.toBe(boom);
    });
  });

  describe('login', () => {
    it('returns a token for valid credentials', async () => {
      usersService.findByEmail.mockResolvedValue(fakeUser);
      hashingService.compare.mockResolvedValue(true);
      jwtService.signAsync.mockResolvedValue('signed.jwt.token');

      const result = await service.login({
        email: 'demo@invoiceapp.com',
        password: 'password123',
      });

      expect(result).toEqual({ accessToken: 'signed.jwt.token' });
    });

    it('throws UnauthorizedException when the email is unknown', async () => {
      usersService.findByEmail.mockResolvedValue(null);

      await expect(service.login({ email: 'ghost@invoiceapp.com', password: 'whatever' })).rejects.toBeInstanceOf(
        UnauthorizedException
      );
      expect(hashingService.compare).not.toHaveBeenCalled();
    });

    it('throws UnauthorizedException when the password does not match', async () => {
      usersService.findByEmail.mockResolvedValue(fakeUser);
      hashingService.compare.mockResolvedValue(false);

      await expect(service.login({ email: 'demo@invoiceapp.com', password: 'wrong' })).rejects.toBeInstanceOf(
        UnauthorizedException
      );
    });

    it('uses an identical error for unknown email and wrong password (no enumeration)', async () => {
      const captureError = async (fn: () => Promise<unknown>): Promise<Error> => {
        try {
          await fn();
          throw new Error('Expected the call to reject, but it resolved');
        } catch (error) {
          if (error instanceof Error) return error;
          throw new Error('An unmapped error footprint was detected.');
        }
      };

      usersService.findByEmail.mockResolvedValueOnce(null);
      const unknownEmailError = await captureError(() =>
        service.login({ email: 'ghost@invoiceapp.com', password: 'x' })
      );

      usersService.findByEmail.mockResolvedValueOnce(fakeUser);
      hashingService.compare.mockResolvedValueOnce(false);
      const wrongPasswordError = await captureError(() =>
        service.login({ email: 'demo@invoiceapp.com', password: 'x' })
      );

      expect(unknownEmailError.message).toEqual(wrongPasswordError.message);
    });
  });
});
