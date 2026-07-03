import { IsEnum } from 'class-validator';
import { InvoiceStatus } from '@prisma/client';

export class TransitionStatusDto {
  @IsEnum(InvoiceStatus)
  status: InvoiceStatus;
}
