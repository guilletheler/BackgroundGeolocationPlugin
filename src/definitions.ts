export interface GtBackgroundGeolocationPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
