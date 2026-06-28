import { Injectable, OnModuleInit, OnModuleDestroy, Logger } from '@nestjs/common';
import { Prisma, PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { Pool } from 'pg';

@Injectable()
export class PrismaService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(PrismaService.name);

  private readonly pool = new Pool({
    connectionString: process.env.DATABASE_URL,
  });

  private static getLogOptions(): Prisma.LogDefinition[] {
    if (process.env.NODE_ENV === 'test') {
      return [];
    }
    return [
      { emit: 'event', level: 'query' },
      { emit: 'stdout', level: 'error' },
      { emit: 'stdout', level: 'warn' },
    ];
  }

  public readonly client = new PrismaClient({
    adapter: new PrismaPg({ connectionString: process.env.DATABASE_URL }),
    log: PrismaService.getLogOptions(),
  });

  async onModuleInit(): Promise<void> {
    this.logger.log('Connecting to PostgreSQL via Driver Adapter...');
    await this.pool.query('SELECT 1');
    this.logger.log('PostgreSQL connection established.');
  }

  async onModuleDestroy(): Promise<void> {
    this.logger.log('Disconnecting pool from PostgreSQL...');
    await this.pool.end();
    this.logger.log('PostgreSQL connection closed.');
  }
}
