import { Test, TestingModule } from '@nestjs/testing';
import { BadRequestException, NotFoundException, UnprocessableEntityException } from '@nestjs/common';
import { InvoiceStatus, Prisma } from '@prisma/client';
import { InvoiceService } from './invoice.service';
import { PrismaService } from '../prisma/prisma.service';
import { CreateInvoiceDto } from './dto/create-invoice.dto';

interface MockPrisma {
  client: { findFirst: jest.Mock };
  invoice: {
    create: jest.Mock;
    findFirst: jest.Mock;
    findMany: jest.Mock;
    count: jest.Mock;
    update: jest.Mock;
    delete: jest.Mock;
  };
  lineItem: { deleteMany: jest.Mock };
  invoiceCounter: { upsert: jest.Mock };
  $transaction: jest.Mock;
}

type TransactionArg = ((tx: MockPrisma) => Promise<unknown>) | Promise<unknown>[];

describe('InvoiceService', () => {
  let service: InvoiceService;
  let prisma: MockPrisma;

  const userId = 'user-uuid-1';
  const clientId = 'client-uuid-1';

  const fakeClient = {
    id: clientId,
    ownerId: userId,
    name: 'Acme Corp',
    email: null,
    phone: null,
    address: null,
    vatNumber: null,
    version: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  const fakeInvoice = () => ({
    id: 'invoice-uuid-1',
    invoiceNumber: 'INV-2026-0001',
    clientId,
    createdById: userId,
    status: InvoiceStatus.DRAFT,
    issueDate: new Date('2026-07-02T00:00:00.000Z'),
    dueDate: new Date('2026-08-01T00:00:00.000Z'),
    subtotal: new Prisma.Decimal('330'),
    taxRate: new Prisma.Decimal('0.2'),
    taxAmount: new Prisma.Decimal('66'),
    discountAmount: new Prisma.Decimal('0'),
    total: new Prisma.Decimal('396'),
    notes: null,
    version: 0,
    createdAt: new Date('2026-07-02T10:00:00.000Z'),
    updatedAt: new Date('2026-07-02T10:00:00.000Z'),
    lineItems: [
      {
        id: 'line-uuid-1',
        invoiceId: 'invoice-uuid-1',
        description: 'Consulting',
        quantity: new Prisma.Decimal('2'),
        unitPrice: new Prisma.Decimal('100'),
        discountPct: new Prisma.Decimal('0.1'),
        lineTotal: new Prisma.Decimal('180'),
        position: 0,
        createdAt: new Date(),
      },
      {
        id: 'line-uuid-2',
        invoiceId: 'invoice-uuid-1',
        description: 'Support',
        quantity: new Prisma.Decimal('3'),
        unitPrice: new Prisma.Decimal('50'),
        discountPct: new Prisma.Decimal('0'),
        lineTotal: new Prisma.Decimal('150'),
        position: 1,
        createdAt: new Date(),
      },
    ],
  });

  const createDto: CreateInvoiceDto = {
    clientId,
    issueDate: '2026-07-02',
    dueDate: '2026-08-01',
    taxRate: 0.2,
    lineItems: [
      { description: 'Consulting', quantity: 2, unitPrice: 100, discountPct: 0.1 },
      { description: 'Support', quantity: 3, unitPrice: 50 },
    ],
  };

  beforeEach(async () => {
    prisma = {
      client: { findFirst: jest.fn() },
      invoice: {
        create: jest.fn(),
        findFirst: jest.fn(),
        findMany: jest.fn(),
        count: jest.fn(),
        update: jest.fn(),
        delete: jest.fn(),
      },
      lineItem: { deleteMany: jest.fn() },
      invoiceCounter: { upsert: jest.fn() },
      $transaction: jest.fn(),
    };

    prisma.$transaction.mockImplementation((arg: TransactionArg) =>
      typeof arg === 'function' ? arg(prisma) : Promise.all(arg)
    );

    const module: TestingModule = await Test.createTestingModule({
      providers: [InvoiceService, { provide: PrismaService, useValue: prisma }],
    }).compile();

    service = module.get(InvoiceService);
  });

  describe('create', () => {
    beforeEach(() => {
      prisma.client.findFirst.mockResolvedValue(fakeClient);
      prisma.invoiceCounter.upsert.mockResolvedValue({ year: 2026, lastValue: 1 });
      prisma.invoice.create.mockResolvedValue(fakeInvoice());
    });

    it('verifies the invoiced client belongs to the caller', async () => {
      await service.create(userId, createDto);

      expect(prisma.client.findFirst).toHaveBeenCalledWith({
        where: { id: clientId, ownerId: userId },
      });
    });

    it('throws NotFoundException and never writes when the client is not owned', async () => {
      prisma.client.findFirst.mockResolvedValue(null);

      await expect(service.create(userId, createDto)).rejects.toBeInstanceOf(NotFoundException);
      expect(prisma.invoice.create).not.toHaveBeenCalled();
      expect(prisma.invoiceCounter.upsert).not.toHaveBeenCalled();
    });

    it('rejects dueDate before issueDate without starting a transaction', async () => {
      await expect(
        service.create(userId, {
          ...createDto,
          issueDate: '2026-08-01',
          dueDate: '2026-07-02',
        })
      ).rejects.toBeInstanceOf(BadRequestException);
      expect(prisma.$transaction).not.toHaveBeenCalled();
    });

    it('persists server-computed totals as fixed-precision strings', async () => {
      await service.create(userId, createDto);

      expect(prisma.invoice.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            subtotal: '330.0000',
            taxAmount: '66.0000',
            total: '396.0000',
            taxRate: '0.2000',
          }),
        })
      );
    });

    it('writes line items with computed lineTotals and stable positions', async () => {
      await service.create(userId, createDto);

      const createArgs = prisma.invoice.create.mock.calls[0][0];
      expect(createArgs.data.lineItems.create).toEqual([
        expect.objectContaining({
          description: 'Consulting',
          lineTotal: '180.0000',
          position: 0,
        }),
        expect.objectContaining({
          description: 'Support',
          lineTotal: '150.0000',
          position: 1,
        }),
      ]);
    });

    it('draws a year-keyed number via atomic upsert-increment and zero-pads it', async () => {
      prisma.invoiceCounter.upsert.mockResolvedValue({ year: 2026, lastValue: 42 });

      await service.create(userId, createDto);

      expect(prisma.invoiceCounter.upsert).toHaveBeenCalledWith({
        where: { year: 2026 },
        create: { year: 2026, lastValue: 1 },
        update: { lastValue: { increment: 1 } },
      });
      expect(prisma.invoice.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({ invoiceNumber: 'INV-2026-0042' }),
        })
      );
    });

    it('returns the serialized response with money as strings', async () => {
      const result = await service.create(userId, createDto);

      expect(result.subtotal).toBe('330.0000');
      expect(result.total).toBe('396.0000');
      expect(result.lineItems[0].lineTotal).toBe('180.0000');
    });
  });

  describe('findAll', () => {
    it('scopes to the caller and applies the status filter', async () => {
      prisma.invoice.findMany.mockResolvedValue([fakeInvoice()]);
      prisma.invoice.count.mockResolvedValue(1);

      const result = await service.findAll(userId, {
        page: 1,
        limit: 20,
        status: InvoiceStatus.DRAFT,
      });

      expect(prisma.invoice.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { createdById: userId, status: InvoiceStatus.DRAFT },
          skip: 0,
          take: 20,
        })
      );
      expect(result.meta).toEqual({ total: 1, page: 1, limit: 20, totalPages: 1 });
      expect(result.data[0].invoiceNumber).toBe('INV-2026-0001');
    });
  });

  describe('findOne', () => {
    it('returns the serialized invoice when owned', async () => {
      prisma.invoice.findFirst.mockResolvedValue(fakeInvoice());

      const result = await service.findOne(userId, 'invoice-uuid-1');

      expect(prisma.invoice.findFirst).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: 'invoice-uuid-1', createdById: userId },
        })
      );
      expect(result.total).toBe('396.0000');
    });

    it('throws NotFoundException when missing or not owned', async () => {
      prisma.invoice.findFirst.mockResolvedValue(null);

      await expect(service.findOne(userId, 'invoice-uuid-1')).rejects.toBeInstanceOf(NotFoundException);
    });
  });

  describe('transitionStatus', () => {
    it('applies a legal transition and bumps the version', async () => {
      prisma.invoice.findFirst.mockResolvedValue(fakeInvoice());
      prisma.invoice.update.mockResolvedValue({
        ...fakeInvoice(),
        status: InvoiceStatus.SENT,
      });

      const result = await service.transitionStatus(userId, 'invoice-uuid-1', {
        status: InvoiceStatus.SENT,
      });

      expect(prisma.invoice.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: 'invoice-uuid-1' },
          data: { status: InvoiceStatus.SENT, version: { increment: 1 } },
        })
      );
      expect(result.status).toBe(InvoiceStatus.SENT);
    });

    it('rejects an illegal transition with 422 and never writes', async () => {
      prisma.invoice.findFirst.mockResolvedValue(fakeInvoice());

      await expect(
        service.transitionStatus(userId, 'invoice-uuid-1', {
          status: InvoiceStatus.PAID,
        })
      ).rejects.toBeInstanceOf(UnprocessableEntityException);
      expect(prisma.invoice.update).not.toHaveBeenCalled();
    });

    it('names the terminal state when transitioning out of PAID', async () => {
      prisma.invoice.findFirst.mockResolvedValue({
        ...fakeInvoice(),
        status: InvoiceStatus.PAID,
      });

      await expect(
        service.transitionStatus(userId, 'invoice-uuid-1', {
          status: InvoiceStatus.SENT,
        })
      ).rejects.toThrow(/terminal state/);
    });
  });

  describe('update', () => {
    it('rejects edits to non-DRAFT invoices with 422 and never writes', async () => {
      prisma.invoice.findFirst.mockResolvedValue({
        ...fakeInvoice(),
        status: InvoiceStatus.SENT,
      });

      await expect(service.update(userId, 'invoice-uuid-1', { notes: 'test' })).rejects.toBeInstanceOf(
        UnprocessableEntityException
      );
      expect(prisma.invoice.update).not.toHaveBeenCalled();
      expect(prisma.lineItem.deleteMany).not.toHaveBeenCalled();
    });

    it('replaces line items wholesale and recomputes totals', async () => {
      prisma.invoice.findFirst.mockResolvedValue(fakeInvoice());
      prisma.invoice.update.mockResolvedValue(fakeInvoice());

      await service.update(userId, 'invoice-uuid-1', {
        lineItems: [{ description: 'Revised scope', quantity: 1, unitPrice: 500 }],
      });

      expect(prisma.lineItem.deleteMany).toHaveBeenCalledWith({
        where: { invoiceId: 'invoice-uuid-1' },
      });

      expect(prisma.invoice.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            subtotal: '500.0000',
            taxAmount: '100.0000',
            total: '600.0000',
            version: { increment: 1 },
          }),
        })
      );
    });

    it('recomputes from EXISTING line items when only the tax rate changes', async () => {
      prisma.invoice.findFirst.mockResolvedValue(fakeInvoice());
      prisma.invoice.update.mockResolvedValue(fakeInvoice());

      await service.update(userId, 'invoice-uuid-1', { taxRate: 0.1 });

      expect(prisma.lineItem.deleteMany).not.toHaveBeenCalled();
      expect(prisma.invoice.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            subtotal: '330.0000',
            taxAmount: '33.0000',
            total: '363.0000',
          }),
        })
      );
    });

    it('validates the EFFECTIVE date pair (new dueDate vs existing issueDate)', async () => {
      prisma.invoice.findFirst.mockResolvedValue(fakeInvoice());

      await expect(service.update(userId, 'invoice-uuid-1', { dueDate: '2026-07-01' })).rejects.toBeInstanceOf(
        BadRequestException
      );
      expect(prisma.invoice.update).not.toHaveBeenCalled();
    });
  });

  describe('remove', () => {
    it('deletes a DRAFT invoice', async () => {
      prisma.invoice.findFirst.mockResolvedValue(fakeInvoice());
      prisma.invoice.delete.mockResolvedValue(fakeInvoice());

      await service.remove(userId, 'invoice-uuid-1');

      expect(prisma.invoice.delete).toHaveBeenCalledWith({
        where: { id: 'invoice-uuid-1' },
      });
    });

    it('rejects deleting a non-DRAFT invoice with 422 and never deletes', async () => {
      prisma.invoice.findFirst.mockResolvedValue({
        ...fakeInvoice(),
        status: InvoiceStatus.PAID,
      });

      await expect(service.remove(userId, 'invoice-uuid-1')).rejects.toBeInstanceOf(UnprocessableEntityException);
      expect(prisma.invoice.delete).not.toHaveBeenCalled();
    });
  });
});
