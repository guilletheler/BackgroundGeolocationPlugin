import { WebPlugin } from '@capacitor/core';

import type {
  GrantedPermissions,
  GtBackgroundGeolocationConfig,
  GtBackgroundGeolocationPlugin,
  Location,
} from './definitions';

export class GtBackgroundGeolocationWeb
  extends WebPlugin
  implements GtBackgroundGeolocationPlugin {

  async configure(
    _options: GtBackgroundGeolocationConfig,
  ): Promise<void> {
    console.warn('GtBackgroundGeolocation.configure is not implemented on the web.');
    return Promise.resolve();
  }

  async start(): Promise<void> {
    console.warn('GtBackgroundGeolocation.start is not implemented on the web.');
    return Promise.resolve();
  }

  async stop(): Promise<void> {
    console.warn('GtBackgroundGeolocation.stop is not implemented on the web.');
    return Promise.resolve();
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
}
