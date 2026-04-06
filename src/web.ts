import { WebPlugin } from '@capacitor/core';

import type {
  GrantedPermissions,
  GtBackgroundGeolocationConfig,
  GtBackgroundGeolocationPlugin,
  Location,
  ServiceStatus,
  Trip,
} from './definitions';

export class GtBackgroundGeolocationWeb
  extends WebPlugin
  implements GtBackgroundGeolocationPlugin {

  async getStatus(): Promise<ServiceStatus> {
    throw this.unimplemented('getStatus is not available on web.');
  }

  async configure(
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _options: GtBackgroundGeolocationConfig,
  ): Promise<void> {
    throw this.unimplemented('configure is not available on web.');
  }

  async start(): Promise<void> {
    throw this.unimplemented('start is not available on web.');
  }

  async stop(): Promise<void> {
    throw this.unimplemented('stop is not available on web.');
  }

  async getCurrentPosition(): Promise<Location> {
    throw this.unimplemented('getCurrentPosition is not available on web.');
  }

  async checkPermissions(): Promise<GrantedPermissions> {
    throw this.unimplemented('checkPermissions is not available on web.');
  }

  async requestPermissions(): Promise<void> {
    throw this.unimplemented('requestPermissions is not available on web.');
  }

  async initTrip(): Promise<void> {
    throw this.unimplemented('initTrip is not available on web.');
  }

  async getTripDistance(): Promise<Trip> {
    throw this.unimplemented('getTripDistance is not available on web.');
  }

  async endTrip(): Promise<Trip> {
    throw this.unimplemented('endTrip is not available on web.');
  }
}
