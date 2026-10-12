package com.fongmi.android.tv.net;

import com.fongmi.android.tv.bean.WebResource;

import java.util.Arrays;
import java.util.List;

/** A bundled snapshot of the public ZhuijuMe guide. */
public final class GuideCatalog {
    private GuideCatalog() {}

    public static List<String> categories() {
        return Arrays.asList("全部", "在线影视", "影视APP", "网盘搜索", "磁力BT", "字幕组", "TVBox软件", "TVBox接口", "直播源", "开源项目");
    }

    public static List<WebResource> all() {
        return Arrays.asList(
                item("创享影视", "无广告三网高峰秒播", "在线影视", "https://ys.cxkjg.cn/"),
                item("洛雪TV", "4K无广告在线影视", "在线影视", "https://tv.lxyy.club/"),
                item("西瓜影视", "没有搜不到的资源", "在线影视", "https://www.kanmovie.com/"),
                item("PPnix", "在线观看电影电视剧", "在线影视", "https://www.ppnix.com/"),
                item("可可影视", "最新 Netflix 新剧与韩国电影", "在线影视", "https://www.kkys14.com/"),
                item("ZIP0", "在线影视搜索与播放", "在线影视", "https://zip0.com/"),
                item("66大片网", "免费电影在线观看", "在线影视", "https://www.77dpw.vip/"),
                item("嘀嗒影视", "高分电影聚集地", "在线影视", "https://www.didahd.xyz/"),
                item("追影", "蓝光高清无广告在线影视", "在线影视", "https://zhuiying3.cc/"),
                item("歪比巴卜", "高清视频免费在线观看", "在线影视", "https://wbbb1.com/"),
                item("开心影院", "最新电影热播电视剧", "在线影视", "https://www.kxyy1.cc/"),
                item("豆花电影网", "最新免费电影与电视剧", "在线影视", "https://dhvideo.cc/"),
                item("饭搭子影视", "热门电影电视剧动漫综艺", "在线影视", "https://fdzys.com/"),
                item("SA视频", "电影电视剧动漫综艺", "在线影视", "https://www.lsjys11.com/"),
                item("91毒舌", "Netflix 新剧与 4K 高清", "在线影视", "https://www.duse0.com/"),
                item("好好看", "4K 高清 Netflix 新剧", "在线影视", "https://www.hhkan2.com/"),
                item("蛋蛋魔法", "国内外热播剧与电影", "在线影视", "https://ddmf.net/"),
                item("青空次元", "动漫在线站", "在线影视", "https://www.sorani.net/"),
                item("APP影院", "国内外热播剧，纯净无广告", "在线影视", "https://www.appmovie.art/"),
                item("Auete影视", "蓝光超清精品影视", "在线影视", "https://www.aeete.com/"),
                item("电影人生", "高清电影在线观看", "在线影视", "https://dyrs.tv/"),
                item("奈飞工厂", "最新美剧，无广告", "在线影视", "https://www.netflixgc.com/"),
                item("白嫖者联盟", "热门热播剧，无广告", "在线影视", "https://www.tdgo.shop/"),
                item("爱电影", "最新热门电影电视剧", "在线影视", "https://kuhh4jo.com/"),
                item("937影视", "看全网影视剧", "在线影视", "https://www.937tv.vip/"),
                item("注视影视", "国内海外影视剧资源", "在线影视", "https://gaze.red/"),
                item("iOS影视APP", "iOS 可用影视 App 合集", "影视APP", "https://b.zhuiju.me/archives/23/"),
                item("KK网盘搜", "夸克、百度网盘搜索", "网盘搜索", "https://kkso.net/"),
                item("PanSou", "纯净的夸克网盘资源搜索", "网盘搜索", "https://pansou.de/"),
                item("追剧吧", "国内外热门影视网盘资源搜索", "网盘搜索", "https://www.zhuiju.us/"),
                item("谷哥搜", "支持多种网盘的综合搜索", "网盘搜索", "https://gugeso.com/"),
                item("BD影视聚合", "高清电影电视剧下载", "磁力BT", "https://www.juhebd.com/"),
                item("BD电影首发", "4K蓝光高清电影更新", "磁力BT", "https://www.bdflixs.com/"),
                item("磁力帝", "磁力资源地址发布页", "磁力BT", "https://www.cld123.com/"),
                item("BT世界网", "高清电影美剧 BT 下载", "磁力BT", "https://www.btsj6.com/"),
                item("BT影视", "4K高清电影 BT 下载", "磁力BT", "https://www.btbtla.com/"),
                item("磁力熊", "无删减、无广告资源", "磁力BT", "https://www.cilixiong.org/"),
                item("ACG.RIP", "动漫、OST 磁力资源", "磁力BT", "https://acg.rip/"),
                item("SeedHub", "影视与动漫资源分享", "磁力BT", "https://sidhub.cc/"),
                item("POMO", "4K原盘免费下载", "磁力BT", "https://pomo.mom/"),
                item("美剧粉", "高清美剧资源", "磁力BT", "https://www.mjf2020.com/"),
                item("电影港网", "无水印4K高清 BT 资源", "磁力BT", "https://www.dyg22.com/"),
                item("高清MP4", "免费1080P与4K电影", "磁力BT", "https://www.mp4ba.vip/"),
                item("字幕库", "影视字幕下载", "字幕组", "https://zimuku.org/"),
                item("射手网(伪)", "字幕下载平台", "字幕组", "https://assrt.net/"),
                item("SubHD", "字幕分享下载平台", "字幕组", "https://subhd.tv/"),
                item("影视仓", "TVBox 二次开发应用", "TVBox软件", "https://b.zhuiju.me/archives/15/"),
                item("FongMi 蜂蜜", "基于 CatVod 的 Android 影音应用", "TVBox软件", "https://b.zhuiju.me/archives/16/"),
                item("Kodi", "开源跨平台家庭媒体中心", "TVBox软件", "https://kodi.tv/"),
                item("TVBox/影视仓配置地址", "最新可用的接口配置地址", "TVBox接口", "https://b.zhuiju.me/archives/3/"),
                item("IPTV神器Pro", "每日更新免费 IPTV 直播源", "直播源", "https://iptv.cqshushu.com/"),
                item("iptv", "全球公开 IPTV 频道集合", "开源项目", "https://github.com/iptv-org/iptv"),
                item("Kazumi", "免费无广告的番剧采集与在线观看", "开源项目", "https://github.com/Predidit/Kazumi"),
                item("Animeko", "一站式弹幕追番平台", "开源项目", "https://github.com/open-ani/animeko"),
                item("pansou", "高性能网盘资源搜索 API", "开源项目", "https://github.com/fish2018/pansou"),
                item("FongMi TV", "基于 CatVod 的开源 Android 影音应用", "开源项目", "https://github.com/FongMi/TV")
        );
    }

    private static WebResource item(String title, String description, String category, String url) {
        return new WebResource(title, description, category, url);
    }
}
