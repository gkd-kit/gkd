import { defineAppConfig } from '../types';

export default defineAppConfig({
  id: 'com.chaozh.iReader.dj',
  name: '得间免费小说',
  groups: [
    {
      key: 1,
      name: '开屏广告',
      activityIds: ['com.chaozh.iReader.ui.activity.WelcomeActivity'],
      rules: [
        {
          matches: '[id$="com.byted.pangle:id/tt_splash_skip_btn"]',
          snapshotUrls: 'https://gkd-kit.gitee.io/snapshot/1698220670268',
        },
      ],
    },
  ],
});