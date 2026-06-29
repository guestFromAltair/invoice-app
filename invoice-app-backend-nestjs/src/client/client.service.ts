import { Injectable, NotFoundException } from '@nestjs/common';
import { Client, Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { CreateClientDto } from './dto/create-client.dto';
import { QueryClientsDto } from './dto/query-clients.dto';
import { ClientResponse } from './types/client-response.type';
import { PaginatedResult } from '../common/types/paginated-result.type';
import { UpdateClientDto } from './dto/update-client.dto';

@Injectable()
export class ClientService {
  constructor(private readonly prisma: PrismaService) {}

  async create(ownerId: string, dto: CreateClientDto): Promise<ClientResponse> {
    return this.prisma.client.create({ data: { ...dto, ownerId } });
  }

  async findAll(ownerId: string, query: QueryClientsDto): Promise<PaginatedResult<ClientResponse>> {
    const { page = 1, limit = 20, search } = query;

    const where: Prisma.ClientWhereInput = {
      ownerId,
      ...(search ? { name: { contains: search, mode: 'insensitive' } } : {}),
    };

    const [data, total] = await this.prisma.$transaction([
      this.prisma.client.findMany({
        where,
        skip: (page - 1) * limit,
        take: limit,
        orderBy: { createdAt: 'desc' },
      }),
      this.prisma.client.count({ where }),
    ]);

    return {
      data,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async findOne(ownerId: string, id: string): Promise<ClientResponse> {
    return this.ensureOwnedClient(ownerId, id);
  }

  async update(ownerId: string, id: string, dto: UpdateClientDto): Promise<ClientResponse> {
    await this.ensureOwnedClient(ownerId, id);

    return this.prisma.client.update({
      where: { id },
      data: dto,
    });
  }

  async remove(ownerId: string, id: string): Promise<void> {
    await this.ensureOwnedClient(ownerId, id);

    await this.prisma.client.delete({
      where: { id },
    });
  }

  private async ensureOwnedClient(ownerId: string, id: string): Promise<Client> {
    const client = await this.prisma.client.findFirst({ where: { id, ownerId } });

    if (!client) {
      throw new NotFoundException(`Client with ID "${id}" not found`);
    }

    return client;
  }
}
