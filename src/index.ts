import { registerPlugin } from '@capacitor/core';

import type { GtBackgroundGeolocationPlugin } from './definitions';

const GtBackgroundGeolocation = registerPlugin<GtBackgroundGeolocationPlugin>('GtBackgroundGeolocation', {
  web: () => import('./web').then((m) => new m.GtBackgroundGeolocationWeb()),
});

export * from './definitions';
export { GtBackgroundGeolocation };
