import { registerAs } from '@nestjs/config';

export interface JwtConfig {
  secret: string;
  expiresIn: number;
}

export default registerAs<JwtConfig>('jwt', () => ({
  secret: process.env.JWT_SECRET as string,
  expiresIn: Math.floor(Number(process.env.JWT_EXPIRATION_MS) / 1000),
}));
