import { Test, TestingModule } from '@nestjs/testing';
import { NotFoundException } from '@nestjs/common';
import { Client } from '@prisma/client';
import { ClientService } from './client.service';
import { PrismaService } from '../prisma/prisma.service';
import { CreateClientDto } from './dto/create-client.dto';

interface MockPrisma {
  client: {
    create: jest.Mock;
    findFirst: jest.Mock;
    findMany: jest.Mock;
    count: jest.Mock;
    update: jest.Mock;
    delete: jest.Mock;
  };
  $transaction: jest.Mock;
}

describe('ClientService', () => {
  let service: ClientService;
  let prisma: MockPrisma;

  const ownerId = 'owner-uuid-1';

  const fakeClient: Client = {
    id: 'client-uuid-1',
    ownerId,
    name: 'Acme Corp',
    email: null,
    phone: null,
    address: null,
    vatNumber: null,
    version: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  beforeEach(async () => {
    prisma = {
      client: {
        create: jest.fn(),
        findFirst: jest.fn(),
        findMany: jest.fn(),
        count: jest.fn(),
        update: jest.fn(),
        delete: jest.fn(),
      },
      $transaction: jest.fn(),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [ClientService, { provide: PrismaService, useValue: prisma }],
    }).compile();

    service = module.get(ClientService);
  });

  describe('create', () => {
    it('injects ownerId from the caller (never the body) and persists', async () => {
      prisma.client.create.mockResolvedValue(fakeClient);
      const dto: CreateClientDto = { name: 'Acme Corp', email: 'a@acme.com' };

      const result = await service.create(ownerId, dto);

      expect(prisma.client.create).toHaveBeenCalledWith({ data: { ...dto, ownerId } });
      expect(result).toEqual(fakeClient);
    });
  });

  describe('findAll', () => {
    beforeEach(() => {
      prisma.$transaction.mockImplementation((ops: Promise<unknown>[]) => Promise.all(ops));
    });

    it('scopes the query to the owner and returns the paginated envelope', async () => {
      prisma.client.findMany.mockResolvedValue([fakeClient]);
      prisma.client.count.mockResolvedValue(1);

      const result = await service.findAll(ownerId, { page: 1, limit: 20 });

      expect(prisma.client.findMany).toHaveBeenCalledWith(
        expect.objectContaining({ where: { ownerId }, skip: 0, take: 20 })
      );
      expect(result.data).toEqual([fakeClient]);
      expect(result.meta).toEqual({
        total: 1,
        page: 1,
        limit: 20,
        totalPages: 1,
      });
    });

    it('computes skip from page and limit', async () => {
      prisma.client.findMany.mockResolvedValue([]);
      prisma.client.count.mockResolvedValue(0);

      await service.findAll(ownerId, { page: 3, limit: 10 });

      expect(prisma.client.findMany).toHaveBeenCalledWith(expect.objectContaining({ skip: 20, take: 10 }));
    });

    it('applies a case-insensitive name filter when search is provided', async () => {
      prisma.client.findMany.mockResolvedValue([]);
      prisma.client.count.mockResolvedValue(0);

      await service.findAll(ownerId, { page: 1, limit: 20, search: 'acme' });

      expect(prisma.client.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { ownerId, name: { contains: 'acme', mode: 'insensitive' } },
        })
      );
    });

    it('computes totalPages with ceiling division', async () => {
      prisma.client.findMany.mockResolvedValue([]);
      prisma.client.count.mockResolvedValue(25);

      const result = await service.findAll(ownerId, { page: 1, limit: 10 });

      expect(result.meta.totalPages).toBe(3);
    });
  });

  describe('findOne', () => {
    it('returns the client when it belongs to the owner', async () => {
      prisma.client.findFirst.mockResolvedValue(fakeClient);

      const result = await service.findOne(ownerId, 'client-uuid-1');

      expect(prisma.client.findFirst).toHaveBeenCalledWith({
        where: { id: 'client-uuid-1', ownerId },
      });
      expect(result).toEqual(fakeClient);
    });

    it('throws NotFoundException when missing or not owned', async () => {
      prisma.client.findFirst.mockResolvedValue(null);

      await expect(service.findOne(ownerId, 'client-uuid-1')).rejects.toBeInstanceOf(NotFoundException);
    });
  });

  describe('update', () => {
    it('updates only after confirming ownership', async () => {
      prisma.client.findFirst.mockResolvedValue(fakeClient);
      prisma.client.update.mockResolvedValue({
        ...fakeClient,
        name: 'Renamed',
      });

      const result = await service.update(ownerId, 'client-uuid-1', {
        name: 'Renamed',
      });

      expect(prisma.client.findFirst).toHaveBeenCalledWith({
        where: { id: 'client-uuid-1', ownerId },
      });
      expect(prisma.client.update).toHaveBeenCalledWith({
        where: { id: 'client-uuid-1' },
        data: { name: 'Renamed' },
      });
      expect(result.name).toBe('Renamed');
    });

    it('throws NotFoundException and never writes for a non-owned client', async () => {
      prisma.client.findFirst.mockResolvedValue(null);

      await expect(service.update(ownerId, 'client-uuid-1', { name: 'x' })).rejects.toBeInstanceOf(NotFoundException);
      expect(prisma.client.update).not.toHaveBeenCalled();
    });
  });

  describe('remove', () => {
    it('deletes only after confirming ownership', async () => {
      prisma.client.findFirst.mockResolvedValue(fakeClient);
      prisma.client.delete.mockResolvedValue(fakeClient);

      await service.remove(ownerId, 'client-uuid-1');

      expect(prisma.client.delete).toHaveBeenCalledWith({
        where: { id: 'client-uuid-1' },
      });
    });

    it('throws NotFoundException and never deletes a non-owned client', async () => {
      prisma.client.findFirst.mockResolvedValue(null);

      await expect(service.remove(ownerId, 'client-uuid-1')).rejects.toBeInstanceOf(NotFoundException);
      expect(prisma.client.delete).not.toHaveBeenCalled();
    });
  });
});
