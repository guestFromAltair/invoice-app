import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { CreateInvoiceDto } from './dto/create-invoice.dto';
import { QueryInvoicesDto } from './dto/query-invoices.dto';
import { InvoiceResponse } from './types/invoice-response.type';
import { PaginatedResult } from '../common/types/paginated-result.type';
import { computeInvoiceTotals } from './domain/invoice-calculator';
import { serializeInvoice } from './serialization/invoice.serializer';

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
        include: { lineItems: { orderBy: { position: 'asc' } } },
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
        include: { lineItems: { orderBy: { position: 'asc' } } },
      }),
      this.prisma.invoice.count({ where }),
    ]);

    return {
      data: rows.map(serializeInvoice),
      meta: { total, page, limit, totalPages: Math.ceil(total / limit) },
    };
  }

  async findOne(userId: string, id: string): Promise<InvoiceResponse> {
    const invoice = await this.prisma.invoice.findFirst({
      where: { id, createdById: userId },
      include: { lineItems: { orderBy: { position: 'asc' } } },
    });

    if (!invoice) {
      throw new NotFoundException(`Invoice with ID "${id}" not found`);
    }

    return serializeInvoice(invoice);
  }
}
