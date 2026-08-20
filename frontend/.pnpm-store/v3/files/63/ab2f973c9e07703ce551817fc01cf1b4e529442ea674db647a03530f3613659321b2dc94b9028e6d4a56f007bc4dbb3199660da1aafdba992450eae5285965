var VERSION_REG = {
  android: /(Android)\s([\d.]+)/,
  ios: /(Version)\/([\d.]+)/
};
var H264_MIMETYPES = ["avc1.42E01E, mp4a.40.2", "avc1.58A01E, mp4a.40.2", "avc1.4D401E, mp4a.40.2", "avc1.64001E, mp4a.40.2", "avc1.42E01E", "mp4v.20.8", "mp4v.20.8, mp4a.40.2", "mp4v.20.240, mp4a.40.2"];
var sniffer = {
  get device() {
    var r = sniffer.os;
    return r.isPc ? "pc" : "mobile";
  },
  get browser() {
    if (typeof navigator === "undefined") {
      return "";
    }
    var ua = navigator.userAgent.toLowerCase();
    var reg = {
      ie: /rv:([\d.]+)\) like gecko/,
      firefox: /firefox\/([\d.]+)/,
      chrome: /chrome\/([\d.]+)/,
      opera: /opera.([\d.]+)/,
      safari: /version\/([\d.]+).*safari/
    };
    return [].concat(Object.keys(reg).filter(function(key) {
      return reg[key].test(ua);
    }))[0];
  },
  get os() {
    if (typeof navigator === "undefined") {
      return {};
    }
    var ua = navigator.userAgent;
    var isWindowsPhone = /(?:Windows Phone)/.test(ua);
    var isSymbian = /(?:SymbianOS)/.test(ua) || isWindowsPhone;
    var isTizen = /(?:Tizen)/ig.test(ua);
    var isWebOS = /(?:Web0S)/ig.test(ua);
    var isAndroid = /(?:Android)/.test(ua);
    var isFireFox = /(?:Firefox)/.test(ua);
    var isIpad = /(?:iPad|PlayBook)/.test(ua) || navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1;
    var isTablet = isIpad || isAndroid && !/(?:Mobile)/.test(ua) || isFireFox && /(?:Tablet)/.test(ua);
    var isPhone = /(?:iPhone)/.test(ua) && !isTablet;
    var isPc = !isPhone && !isAndroid && !isSymbian && !isTablet;
    return {
      isTablet,
      isPhone,
      isIpad,
      isIos: isPhone || isIpad,
      isAndroid,
      isPc,
      isSymbian,
      isWindowsPhone,
      isFireFox,
      isTizen,
      isWebOS
    };
  },
  get osVersion() {
    if (typeof navigator === "undefined") {
      return 0;
    }
    var ua = navigator.userAgent;
    var reg = "";
    if (/(?:iPhone)|(?:iPad|PlayBook)/.test(ua)) {
      reg = VERSION_REG.ios;
    } else {
      reg = VERSION_REG.android;
    }
    var _match = reg ? reg.exec(ua) : [];
    if (_match && _match.length >= 3) {
      var version = _match[2].split(".");
      return version.length > 0 ? parseInt(version[0]) : 0;
    }
    return 0;
  },
  get isWeixin() {
    if (typeof navigator === "undefined") {
      return false;
    }
    var reg = /(micromessenger)\/([\d.]+)/;
    var match = reg.exec(navigator.userAgent.toLocaleLowerCase());
    if (match) {
      return true;
    }
    return false;
  },
  isSupportMP4: function isSupportMP4() {
    var result = {
      isSupport: false,
      mime: ""
    };
    if (typeof document === "undefined") {
      return result;
    }
    if (this.supportResult) {
      return this.supportResult;
    }
    var a = document.createElement("video");
    if (typeof a.canPlayType === "function") {
      H264_MIMETYPES.map(function(key) {
        if (a.canPlayType('video/mp4; codecs="'.concat(key, '"')) === "probably") {
          result.isSupport = true;
          result.mime += "||".concat(key);
        }
      });
    }
    this.supportResult = result;
    a = null;
    return result;
  },
  isMSESupport: function isMSESupport() {
    var mime = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : 'video/mp4; codecs="avc1.42E01E,mp4a.40.2"';
    if (typeof MediaSource === "undefined" || !MediaSource)
      return false;
    try {
      return MediaSource.isTypeSupported(mime);
    } catch (error) {
      this._logger.error(mime, error);
      return false;
    }
  },
  isHevcSupported: function isHevcSupported() {
    if (typeof MediaSource === "undefined" || !MediaSource.isTypeSupported) {
      return false;
    }
    return MediaSource.isTypeSupported('video/mp4;codecs="hev1.1.6.L120.90"') || MediaSource.isTypeSupported('video/mp4;codecs="hev1.2.4.L120.90"') || MediaSource.isTypeSupported('video/mp4;codecs="hev1.3.E.L120.90"') || MediaSource.isTypeSupported('video/mp4;codecs="hev1.4.10.L120.90"');
  },
  probeConfigSupported: function probeConfigSupported(info) {
    var defaults = {
      supported: false,
      smooth: false,
      powerEfficient: false
    };
    if (!info || typeof navigator === "undefined") {
      return Promise.resolve(defaults);
    }
    if (navigator.mediaCapabilities && navigator.mediaCapabilities.decodingInfo) {
      return navigator.mediaCapabilities.decodingInfo(info);
    } else {
      var videoConfig = info.video || {};
      var audioConfig = info.audio || {};
      try {
        var videoSupported = MediaSource.isTypeSupported(videoConfig.contentType);
        var audioSupported = MediaSource.isTypeSupported(audioConfig.contentType);
        return Promise.resolve({
          supported: videoSupported && audioSupported,
          smooth: false,
          powerEfficient: false
        });
      } catch (e) {
        return Promise.resolve(defaults);
      }
    }
  }
};
export { sniffer as default };
