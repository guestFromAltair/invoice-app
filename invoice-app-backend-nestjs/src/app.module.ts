import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { APP_FILTER } from '@nestjs/core';
import { PrismaModule } from './prisma/prisma.module';
import { UsersModule } from './users/users.module';
import { AuthModule } from './auth/auth.module';
import { validateEnv } from './common/env.validation';
import { GlobalHttpExceptionFilter } from './common/filters/global-http-exception.filter';
import jwtConfig from './config/jwt.config';
import { ClientModule } from './client/client.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      validate: validateEnv,
      envFilePath: '.env',
      load: [jwtConfig],
    }),
    PrismaModule,
    UsersModule,
    AuthModule,
    ClientModule,
  ],
  providers: [
    {
      provide: APP_FILTER,
      useClass: GlobalHttpExceptionFilter,
    },
  ],
})
export class AppModule {}
