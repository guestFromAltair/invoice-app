import { registerAs } from '@nestjs/config';

export interface RedisConfig {
  host: string;
  port: number;
  password?: string;
}

export default registerAs<RedisConfig>('redis', () => ({
  host: process.env.REDIS_HOST as string,
  port: Number(process.env.REDIS_PORT),
  password: process.env.REDIS_PASSWORD || undefined,
}));
