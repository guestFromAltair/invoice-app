import {
  Injectable,
  OnModuleInit,
  OnModuleDestroy,
  Logger,
} from '@nestjs/common';
import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { Pool } from 'pg';

@Injectable()
export class PrismaService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(PrismaService.name);

  private readonly pool = new Pool({
    connectionString: process.env.DATABASE_URL,
  });

  public readonly client = new PrismaClient({
    adapter: new PrismaPg({ connectionString: process.env.DATABASE_URL }),
    log: [
      { emit: 'event', level: 'query' },
      { emit: 'stdout', level: 'error' },
      { emit: 'stdout', level: 'warn' },
    ],
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
