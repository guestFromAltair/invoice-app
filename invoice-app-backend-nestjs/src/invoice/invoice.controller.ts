import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import { InvoiceService } from './invoice.service';
import { CreateInvoiceDto } from './dto/create-invoice.dto';
import { UpdateInvoiceDto } from './dto/update-invoice.dto';
import { TransitionStatusDto } from './dto/transition-status.dto';
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

  @Patch(':id/status')
  transitionStatus(
    @CurrentUser('id') userId: string,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: TransitionStatusDto
  ): Promise<InvoiceResponse> {
    return this.invoiceService.transitionStatus(userId, id, dto);
  }

  @Patch(':id')
  update(
    @CurrentUser('id') userId: string,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateInvoiceDto
  ): Promise<InvoiceResponse> {
    return this.invoiceService.update(userId, id, dto);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  remove(@CurrentUser('id') userId: string, @Param('id', ParseUUIDPipe) id: string): Promise<void> {
    return this.invoiceService.remove(userId, id);
  }
}
