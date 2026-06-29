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
import { ClientService } from './client.service';
import { CreateClientDto } from './dto/create-client.dto';
import { QueryClientsDto } from './dto/query-clients.dto';
import { ClientResponse } from './types/client-response.type';
import { PaginatedResult } from '../common/types/paginated-result.type';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { UpdateClientDto } from './dto/update-client.dto';

@Controller('clients')
export class ClientController {
  constructor(private readonly clientService: ClientService) {}

  @Post()
  create(@CurrentUser('id') ownerId: string, @Body() dto: CreateClientDto): Promise<ClientResponse> {
    return this.clientService.create(ownerId, dto);
  }

  @Get()
  findAll(
    @CurrentUser('id') ownerId: string,
    @Query() query: QueryClientsDto
  ): Promise<PaginatedResult<ClientResponse>> {
    return this.clientService.findAll(ownerId, query);
  }

  @Get(':id')
  findOne(@CurrentUser('id') ownerId: string, @Param('id', ParseUUIDPipe) id: string): Promise<ClientResponse> {
    return this.clientService.findOne(ownerId, id);
  }

  @Patch(':id')
  update(
    @CurrentUser('id') ownerId: string,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateClientDto
  ): Promise<ClientResponse> {
    return this.clientService.update(ownerId, id, dto);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  remove(@CurrentUser('id') ownerId: string, @Param('id', ParseUUIDPipe) id: string): Promise<void> {
    return this.clientService.remove(ownerId, id);
  }
}
