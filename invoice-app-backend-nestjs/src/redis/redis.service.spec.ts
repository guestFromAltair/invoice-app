import { Test, TestingModule } from '@nestjs/testing';
import { REDIS_CLIENT, RedisService } from './redis.service';

interface MockRedisClient {
  set: jest.Mock;
  get: jest.Mock;
  del: jest.Mock;
  quit: jest.Mock;
}

describe('RedisService', () => {
  let service: RedisService;
  let client: MockRedisClient;

  beforeEach(async () => {
    client = {
      set: jest.fn(),
      get: jest.fn(),
      del: jest.fn(),
      quit: jest.fn().mockResolvedValue('OK'),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [RedisService, { provide: REDIS_CLIENT, useValue: client }],
    }).compile();

    service = module.get(RedisService);
  });

  describe('setIfNotExists (the atomic claim)', () => {
    it('issues a single SET with NX and PX flags', async () => {
      client.set.mockResolvedValue('OK');

      await service.setIfNotExists('idem:key', 'in-progress', 60_000);

      expect(client.set).toHaveBeenCalledWith('idem:key', 'in-progress', 'PX', 60_000, 'NX');
    });

    it('returns true when Redis replies OK (this caller claimed the key)', async () => {
      client.set.mockResolvedValue('OK');

      await expect(service.setIfNotExists('idem:key', 'v', 1000)).resolves.toBe(true);
    });

    it('returns false when Redis replies null (key already held)', async () => {
      client.set.mockResolvedValue(null);

      await expect(service.setIfNotExists('idem:key', 'v', 1000)).resolves.toBe(false);
    });
  });

  describe('setWithTtl', () => {
    it('writes with a PX expiry and WITHOUT the NX flag (unconditional)', async () => {
      client.set.mockResolvedValue('OK');

      await service.setWithTtl('idem:key', 'stored-response', 86_400_000);

      expect(client.set).toHaveBeenCalledWith('idem:key', 'stored-response', 'PX', 86_400_000);
    });
  });

  describe('get', () => {
    it('returns the stored value', async () => {
      client.get.mockResolvedValue('stored-response');

      await expect(service.get('idem:key')).resolves.toBe('stored-response');
      expect(client.get).toHaveBeenCalledWith('idem:key');
    });

    it('returns null for a missing key', async () => {
      client.get.mockResolvedValue(null);

      await expect(service.get('missing')).resolves.toBeNull();
    });
  });

  describe('delete', () => {
    it('deletes the key', async () => {
      client.del.mockResolvedValue(1);

      await service.delete('idem:key');

      expect(client.del).toHaveBeenCalledWith('idem:key');
    });
  });

  describe('lifecycle', () => {
    it('sends a graceful QUIT on module destroy', async () => {
      await service.onModuleDestroy();

      expect(client.quit).toHaveBeenCalledTimes(1);
    });
  });
});
