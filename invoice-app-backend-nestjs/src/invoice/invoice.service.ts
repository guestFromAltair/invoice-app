import { BadRequestException, Injectable, NotFoundException, UnprocessableEntityException } from '@nestjs/common';
import { InvoiceStatus, LineItem, Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { CreateInvoiceDto } from './dto/create-invoice.dto';
import { UpdateInvoiceDto } from './dto/update-invoice.dto';
import { TransitionStatusDto } from './dto/transition-status.dto';
import { QueryInvoicesDto } from './dto/query-invoices.dto';
import { InvoiceResponse } from './types/invoice-response.type';
import { PaginatedResult } from '../common/types/paginated-result.type';
import { computeInvoiceTotals } from './domain/invoice-calculator';
import { canTransition, getAllowedTransitions } from './domain/invoice-status.machine';
import { serializeInvoice } from './serialization/invoice.serializer';

const LINE_ITEMS_ORDERED = {
  lineItems: { orderBy: { position: 'asc' as const } },
};

@Injectable()
export class InvoiceService {
  constructor(private readonly prisma: PrismaService) {}

  async create(userId: string, dto: CreateInvoiceDto): Promise<InvoiceResponse> {
    const issueDate = new Date(dto.issueDate);
    const dueDate = new Date(dto.dueDate);

    if (dueDate < issueDate) {
      throw new BadRequestException('dueDate must not be before issueDate');
    }

    const invoice = await this.prisma.$transaction(async (tx: Prisma.TransactionClient) => {
      const client = await tx.client.findFirst({
        where: { id: dto.clientId, ownerId: userId },
      });
      if (!client) {
        throw new NotFoundException(`Client with ID "${dto.clientId}" not found`);
      }

      const totals = computeInvoiceTotals({
        lineItems: dto.lineItems,
        taxRate: dto.taxRate,
        discountAmount: dto.discountAmount,
      });

      const year = issueDate.getUTCFullYear();
      const counter = await tx.invoiceCounter.upsert({
        where: { year },
        create: { year, lastValue: 1 },
        update: { lastValue: { increment: 1 } },
      });
      const invoiceNumber = `INV-${year}-${String(counter.lastValue).padStart(4, '0')}`;

      return tx.invoice.create({
        data: {
          invoiceNumber,
          clientId: dto.clientId,
          createdById: userId,
          issueDate,
          dueDate,
          subtotal: totals.subtotal.toFixed(4),
          taxRate: totals.taxRate.toFixed(4),
          taxAmount: totals.taxAmount.toFixed(4),
          discountAmount: totals.discountAmount.toFixed(4),
          total: totals.total.toFixed(4),
          notes: dto.notes ?? null,
          lineItems: {
            create: dto.lineItems.map((item, index) => ({
              description: item.description,
              quantity: item.quantity.toFixed(2),
              unitPrice: item.unitPrice.toFixed(4),
              discountPct: (item.discountPct ?? 0).toFixed(4),
              lineTotal: totals.lineTotals[index].toFixed(4),
              position: index,
            })),
          },
        },
        include: LINE_ITEMS_ORDERED,
      });
    });

    return serializeInvoice(invoice);
  }

  async findAll(userId: string, query: QueryInvoicesDto): Promise<PaginatedResult<InvoiceResponse>> {
    const { page = 1, limit = 20, status } = query;

    const where: Prisma.InvoiceWhereInput = {
      createdById: userId,
      ...(status ? { status } : {}),
    };

    const [rows, total] = await this.prisma.$transaction([
      this.prisma.invoice.findMany({
        where,
        skip: (page - 1) * limit,
        take: limit,
        orderBy: { createdAt: 'desc' },
        include: LINE_ITEMS_ORDERED,
      }),
      this.prisma.invoice.count({ where }),
    ]);

    return {
      data: rows.map(serializeInvoice),
      meta: { total, page, limit, totalPages: Math.ceil(total / limit) },
    };
  }

  async findOne(userId: string, id: string): Promise<InvoiceResponse> {
    const invoice = await this.ensureOwnedInvoice(userId, id);
    return serializeInvoice(invoice);
  }

  async transitionStatus(userId: string, id: string, dto: TransitionStatusDto): Promise<InvoiceResponse> {
    const invoice = await this.ensureOwnedInvoice(userId, id);

    if (!canTransition(invoice.status, dto.status)) {
      const allowed = getAllowedTransitions(invoice.status);
      throw new UnprocessableEntityException(
        `Cannot transition invoice from ${invoice.status} to ${dto.status}. ` +
          (allowed.length ? `Allowed transitions: ${allowed.join(', ')}.` : `${invoice.status} is a terminal state.`)
      );
    }

    const updated = await this.prisma.invoice.update({
      where: { id },
      data: { status: dto.status, version: { increment: 1 } },
      include: LINE_ITEMS_ORDERED,
    });

    return serializeInvoice(updated);
  }

  async update(userId: string, id: string, dto: UpdateInvoiceDto): Promise<InvoiceResponse> {
    const existing = await this.ensureOwnedInvoice(userId, id);

    if (existing.status !== InvoiceStatus.DRAFT) {
      throw new UnprocessableEntityException(
        `Only DRAFT invoices can be edited; this invoice is ${existing.status}. ` +
          'A sent invoice is an immutable business record.'
      );
    }

    const issueDate = dto.issueDate ? new Date(dto.issueDate) : existing.issueDate;
    const dueDate = dto.dueDate ? new Date(dto.dueDate) : existing.dueDate;
    if (dueDate < issueDate) {
      throw new BadRequestException('dueDate must not be before issueDate');
    }

    const lineItemInputs =
      dto.lineItems ??
      existing.lineItems.map((item: LineItem) => ({
        quantity: item.quantity.toString(),
        unitPrice: item.unitPrice.toString(),
        discountPct: item.discountPct.toString(),
      }));

    const totals = computeInvoiceTotals({
      lineItems: lineItemInputs,
      taxRate: dto.taxRate ?? existing.taxRate.toString(),
      discountAmount: dto.discountAmount ?? existing.discountAmount.toString(),
    });

    const updated = await this.prisma.$transaction(async (tx: Prisma.TransactionClient) => {
      if (dto.lineItems) {
        await tx.lineItem.deleteMany({ where: { invoiceId: id } });
      }

      return tx.invoice.update({
        where: { id },
        data: {
          issueDate,
          dueDate,
          notes: dto.notes !== undefined ? dto.notes : existing.notes,
          subtotal: totals.subtotal.toFixed(4),
          taxRate: totals.taxRate.toFixed(4),
          taxAmount: totals.taxAmount.toFixed(4),
          discountAmount: totals.discountAmount.toFixed(4),
          total: totals.total.toFixed(4),
          version: { increment: 1 },
          ...(dto.lineItems
            ? {
                lineItems: {
                  create: dto.lineItems.map((item, index) => ({
                    description: item.description,
                    quantity: item.quantity.toFixed(2),
                    unitPrice: item.unitPrice.toFixed(4),
                    discountPct: (item.discountPct ?? 0).toFixed(4),
                    lineTotal: totals.lineTotals[index].toFixed(4),
                    position: index,
                  })),
                },
              }
            : {}),
        },
        include: LINE_ITEMS_ORDERED,
      });
    });

    return serializeInvoice(updated);
  }

  async remove(userId: string, id: string): Promise<void> {
    const existing = await this.ensureOwnedInvoice(userId, id);

    if (existing.status !== InvoiceStatus.DRAFT) {
      throw new UnprocessableEntityException(
        `Only DRAFT invoices can be deleted; this invoice is ${existing.status}. ` +
          'Cancel it instead to preserve the business record.'
      );
    }

    await this.prisma.invoice.delete({ where: { id } });
  }

  private async ensureOwnedInvoice(userId: string, id: string) {
    const invoice = await this.prisma.invoice.findFirst({
      where: { id, createdById: userId },
      include: LINE_ITEMS_ORDERED,
    });

    if (!invoice) {
      throw new NotFoundException(`Invoice with ID "${id}" not found`);
    }

    return invoice;
  }
}
