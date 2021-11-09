package li.songe.ad_closer.data

data class Rule(
    val packageName: String,
    val className: String,
    val selector: String
) {
    companion object {
        val defaultRuleList = listOf<Rule>(
//            Rule(
//                "com.zhihu.android",
//                "com.zhihu.android.mix.activity.ContentMixProfileActivity",
//                "View[text=查看详情] + View[text=×]"
//            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.mix.activity.ContentMixProfileActivity",
                "View[text$=的广告] - View[text=×]"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.mix.activity.ContentMixProfileActivity",
                "TextView[text*=的广告] - Image"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.mix.activity.ContentMixProfileActivity",
                "View[text$=的广告] + View[text=×]"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.mix.activity.ContentMixProfileActivity",
                "View[text$=的广告] +2 View[text=×]"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.mix.activity.ContentMixProfileActivity",
                "View[text$=关注][text*=回答] + View[text=×]"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.mix.activity.ContentMixProfileActivity",
                "View[text*=的回答][text*=点赞][text$=评论] + TextView + Image"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.ContentActivity",
                "TextView[id=com.zhihu.android:id/confirm_uninterest]"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.ContentActivity",
                "TextView[id=com.zhihu.android:id/uninterest_reason]"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.ContentActivity",
                "ViewGroup > TextView[text*=广告] +4 ImageView"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.ContentActivity",
                "ViewGroup > TextView[text*=广告] +2 ImageView"
            ),
            Rule(
                "com.baidu.tieba",
                "com.baidu.tieba.tblauncher.MainTabActivity",
                "FrameLayout[id=com.baidu.tieba:id/ad_close_view]"
            ),
            Rule(
                "com.baidu.tieba",
                "com.baidu.tieba.tblauncher.MainTabActivity",
                "View[id=com.baidu.tieba:id/forbid_thread_btn]"
            ),
            Rule(
                "com.baidu.tieba",
                "com.baidu.tieba.tblauncher.MainTabActivity",
                "ImageView[id=com.baidu.tieba:id/float_layer_feedback_picture]"
            ),
            Rule(
                "com.baidu.tieba",
                "com.baidu.tieba.tblauncher.MainTabActivity",
                "RelativeLayout[id=com.baidu.tieba:id/close_layout]"
            ),
            Rule(
                "com.baidu.tieba",
                "com.baidu.tieba.pb.pb.main.PbActivity",
                "View[id=com.baidu.tieba:id/forbid_thread_btn]"
            ),
            Rule(
                "com.baidu.tieba",
                "com.baidu.tieba.pb.pb.main.PbActivity",
                "FrameLayout[id=com.baidu.tieba:id/ad_close_view]"
            ),
            Rule(
                "tv.danmaku.bili",
                "tv.danmaku.bili.MainActivityV2",
                "TextView[id=tv.danmaku.bili:id/count_down]"
            ),
            Rule(
                "tv.danmaku.bili",
                "tv.danmaku.bili.ui.video.VideoDetailsActivity",
                "ImageView[id=tv.danmaku.bili:id/close]"
            ),
            Rule(
                "com.duokan.phone.remotecontroller",
                "com.xiaomi.mitv.phone.remotecontroller.HoriWidgetMainActivityV2",
                "ImageView[id=com.duokan.phone.remotecontroller:id/image_close_banner]"
            ),
            Rule(
                "com.tencent.mm",
                "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI",
                "TextView[id=com.tencent.mm:id/hus][text=选择后将减少该类推荐] + TextView[id=com.tencent.mm:id/hui][text=确认]"
            ),
            Rule(
                "com.tencent.mm",
                "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI",
                "TextView[id=com.tencent.mm:id/hus][text=选择后将减少该类推荐] + FrameLayout[id=com.tencent.mm:id/huj] > ViewGroup[id=com.tencent.mm:id/hul] > TextView[text=直接关闭]"
            ),
            Rule(
                "com.tencent.mm",
                "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI",
                "TextView[id=com.tencent.mm:id/hus][text*=广告] + FrameLayout[id=com.tencent.mm:id/huj] > LinearLayout[id=com.tencent.mm:id/hum] > LinearLayout[id=com.tencent.mm:id/hue] + LinearLayout[id=com.tencent.mm:id/hup]"
            ),
            Rule(
                "com.tencent.mm",
                "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI",
                "LinearLayout[childCount=2] > LinearLayout[id=com.tencent.mm:id/fzb] > TextView[id=com.tencent.mm:id/fzg] + LinearLayout[id=com.tencent.mm:id/fj][childCount=0]"
            ),
            Rule(
                "com.baidu.tieba",
                "com.baidu.tieba.pb.pb.main.PbActivity",
                "TextView[id=com.baidu.tieba:id/head_text] + View[id=com.baidu.tieba:id/uninterested_btn]"
            ),
            Rule(
                "com.baidu.tieba",
                "com.baidu.tieba.pb.pb.main.PbActivity",
                "ImageView[id=com.baidu.tieba:id/coverView] + TextView[id=com.baidu.tieba:id/alaStateView] + TextView[id=com.baidu.tieba:id/descView] + RelativeLayout + ImageView"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.app.ui.activity.MainActivity",
                "LinearLayout[id=com.zhihu.android:id/content] > RelativeLayout > TextView[id=com.zhihu.android:id/title][text=不感兴趣]"
            ),
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.app.ui.activity.MainActivity",
                "FrameLayout[id=com.zhihu.android:id/ad_float] >> RecyclerView > FrameLayout > ViewGroup > ViewGroup > FrameLayout > ViewGroup[childCount=1] + ViewGroup[childCount=1] > ImageView"
            ),
//            new rule
            Rule(
                "com.zhihu.android",
                "com.zhihu.android.mix.activity.ContentMixProfileActivity",
                "TextView[text*=赞同][text$=评论][text*=·] + TextView[text$=专题精选] + View[text=×]"
            )
//            Rule(
//                "com.coolapk.market",
//                "com.coolapk.market.view.main.MainActivity",
//                "ViewGroup > TextView[text=疑似抄袭]"
//            ),
//            Rule(
//                "com.coolapk.market",
//                "com.coolapk.market.view.main.MainActivity",
//                "Button[text^=看视频][text$=免广告] - Button[text=不感兴趣]"
//            ),
//            Rule(
//                "com.coolapk.market",
//                "com.coolapk.market.view.main.MainActivity",
//                "TextView[id=com.coolapk.market:id/ad_text_view] + ImageView[id=com.coolapk.market:id/close_view]"
//            )
        )
    }
}
