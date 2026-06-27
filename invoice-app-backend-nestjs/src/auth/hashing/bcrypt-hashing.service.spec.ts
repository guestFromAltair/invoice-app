import { BcryptHashingService } from './bcrypt-hashing.service';

jest.mock('bcryptjs', () => jest.requireActual('bcryptjs'));

describe('BcryptHashingService', () => {
  let service: BcryptHashingService;

  beforeEach(() => {
    service = new BcryptHashingService();
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('produces a hash that differs from the plaintext', async () => {
    const hash = await service.hash('password123');
    expect(hash).toBeDefined();
    expect(hash).not.toEqual('password123');
    expect(hash.length).toBeGreaterThan(0);
  });

  it('produces different hashes for the same input (random salt)', async () => {
    const a = await service.hash('password123');
    const b = await service.hash('password123');
    expect(a).not.toEqual(b);
  });

  it('compare() returns true for a matching password', async () => {
    const hash = await service.hash('password123');
    await expect(service.compare('password123', hash)).resolves.toBe(true);
  });

  it('compare() returns false for a non-matching password', async () => {
    const hash = await service.hash('password123');
    await expect(service.compare('wrongpassword', hash)).resolves.toBe(false);
  });
});
