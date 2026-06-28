import { execSync } from 'node:child_process';
import { INestApplication } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { PostgreSqlContainer, StartedPostgreSqlContainer } from '@testcontainers/postgresql';
import { AppModule } from '../../src/app.module';
import { configureApp } from '../../src/app.setup';

export interface E2EContext {
  app: INestApplication;
  container: StartedPostgreSqlContainer;
}

export async function bootstrapE2E(): Promise<E2EContext> {
  const container = await new PostgreSqlContainer('postgres:16-alpine').start();
  const databaseUrl = container.getConnectionUri();

  process.env.DATABASE_URL = databaseUrl;
  process.env.JWT_SECRET = process.env.JWT_SECRET ?? '404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970';
  process.env.JWT_EXPIRATION_MS = process.env.JWT_EXPIRATION_MS ?? '86400000';
  process.env.REDIS_HOST = process.env.REDIS_HOST ?? 'localhost';
  process.env.REDIS_PORT = process.env.REDIS_PORT ?? '6379';
  process.env.ALLOWED_ORIGINS = process.env.ALLOWED_ORIGINS ?? 'http://localhost:5173';
  process.env.PORT = process.env.PORT ?? '3000';
  process.env.NODE_ENV = 'test';

  execSync('npx prisma migrate deploy', {
    env: { ...process.env, DATABASE_URL: databaseUrl },
    stdio: 'inherit',
  });

  const moduleRef: TestingModule = await Test.createTestingModule({
    imports: [AppModule],
  }).compile();

  const app = moduleRef.createNestApplication();
  configureApp(app);
  await app.init();

  return { app, container };
}

export async function teardownE2E(context: E2EContext): Promise<void> {
  await context.app.close();
  await context.container.stop();
}
