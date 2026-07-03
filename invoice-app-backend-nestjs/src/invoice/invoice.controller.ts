import { Body, Controller, Get, Param, ParseUUIDPipe, Post, Query } from '@nestjs/common';
import { InvoiceService } from './invoice.service';
import { CreateInvoiceDto } from './dto/create-invoice.dto';
import { QueryInvoicesDto } from './dto/query-invoices.dto';
import { InvoiceResponse } from './types/invoice-response.type';
import { PaginatedResult } from '../common/types/paginated-result.type';
import { CurrentUser } from '../common/decorators/current-user.decorator';

@Controller('invoices')
export class InvoiceController {
  constructor(private readonly invoiceService: InvoiceService) {}

  @Post()
  create(@CurrentUser('id') userId: string, @Body() dto: CreateInvoiceDto): Promise<InvoiceResponse> {
    return this.invoiceService.create(userId, dto);
  }

  @Get()
  findAll(
    @CurrentUser('id') userId: string,
    @Query() query: QueryInvoicesDto
  ): Promise<PaginatedResult<InvoiceResponse>> {
    return this.invoiceService.findAll(userId, query);
  }

  @Get(':id')
  findOne(@CurrentUser('id') userId: string, @Param('id', ParseUUIDPipe) id: string): Promise<InvoiceResponse> {
    return this.invoiceService.findOne(userId, id);
  }
}
