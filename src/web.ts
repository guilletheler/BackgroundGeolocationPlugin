import { WebPlugin } from '@capacitor/core';

import type { GtBackgroundGeolocationPlugin } from './definitions';

export class GtBackgroundGeolocationWeb extends WebPlugin implements GtBackgroundGeolocationPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
