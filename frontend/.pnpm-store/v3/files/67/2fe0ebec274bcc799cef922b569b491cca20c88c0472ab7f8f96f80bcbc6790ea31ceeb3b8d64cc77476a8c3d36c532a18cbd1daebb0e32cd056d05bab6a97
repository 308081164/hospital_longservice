import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass, defineProperty as _defineProperty, get as _get, getPrototypeOf as _getPrototypeOf } from "./_virtual/_rollupPluginBabelHelpers.js";
import EventEmitter from "eventemitter3";
import util from "./utils/util.js";
import sniffer from "./utils/sniffer.js";
import Errors, { ERROR_TYPE_MAP } from "./error.js";
import { VIDEO_EVENTS, SOURCE_SUCCESS, SOURCE_ERROR, URL_CHANGE, WAITING } from "./events.js";
function emitVideoEvent(eventKey, e) {
  if (!this || !this.emit) {
    return;
  }
  if (eventKey === "error") {
    this.errorHandler(eventKey, e.error);
  } else {
    this.emit(eventKey, e);
  }
}
function getVideoEventHandler(eventKey, player) {
  return function(e, _err) {
    var eData = {
      player,
      eventName: eventKey,
      originalEvent: e,
      detail: e.detail || {},
      timeStamp: e.timeStamp,
      currentTime: player.currentTime,
      duration: player.duration,
      paused: player.paused,
      ended: player.ended,
      isInternalOp: !!player._internalOp[e.type],
      muted: player.muted,
      volume: player.volume,
      host: util.getHostFromUrl(player.currentSrc),
      vtype: player.vtype
    };
    player.removeInnerOP(e.type);
    if (eventKey === "timeupdate") {
      player._currentTime = player.media && player.media.currentTime;
    }
    if (eventKey === "ratechange") {
      var _rate = player.media ? player.media.playbackRate : 0;
      if (_rate && player._rate === _rate) {
        return;
      }
      player._rate = player.media && player.media.playbackRate;
    }
    if (eventKey === "durationchange") {
      player._duration = player.media.duration;
    }
    if (eventKey === "volumechange") {
      eData.isMutedChange = player._lastMuted !== player.muted;
      player._lastMuted = player.muted;
    }
    if (eventKey === "error") {
      eData.error = _err || player.video.error;
    }
    if (player.mediaEventMiddleware[eventKey]) {
      var callback = emitVideoEvent.bind(player, eventKey, eData);
      try {
        player.mediaEventMiddleware[eventKey].call(player, eData, callback);
      } catch (err) {
        emitVideoEvent.call(player, eventKey, eData);
        throw err;
      }
    } else {
      emitVideoEvent.call(player, eventKey, eData);
    }
  };
}
var MediaProxy = /* @__PURE__ */ function(_EventEmitter) {
  _inherits(MediaProxy2, _EventEmitter);
  var _super = _createSuper(MediaProxy2);
  function MediaProxy2(options) {
    var _this;
    _classCallCheck(this, MediaProxy2);
    _this = _super.call(this, options);
    _this._hasStart = false;
    _this._currentTime = 0;
    _this._duration = 0;
    _this._internalOp = {};
    _this._lastMuted = false;
    _this.vtype = "MP4";
    _this._rate = -1;
    _this.mediaConfig = Object.assign({}, {
      controls: false,
      autoplay: options.autoplay,
      playsinline: options.playsinline,
      "x5-playsinline": options.playsinline,
      "webkit-playsinline": options.playsinline,
      "x5-video-player-fullscreen": options["x5-video-player-fullscreen"] || options.x5VideoPlayerFullscreen,
      "x5-video-orientation": options["x5-video-orientation"] || options.x5VideoOrientation,
      airplay: options.airplay,
      "webkit-airplay": options.airplay,
      tabindex: options.tabindex | 0,
      mediaType: options.mediaType || "video",
      "data-index": -1
    }, options.videoConfig, options.videoAttributes);
    var playerType = options["x5-video-player-type"] || options.x5VideoPlayerType;
    if (sniffer.isWeixin && sniffer.os.isAndroid && playerType) {
      _this.mediaConfig["x5-video-player-type"] = playerType;
      delete _this.mediaConfig.playsinline;
      delete _this.mediaConfig["webkit-playsinline"];
      delete _this.mediaConfig["x5-playsinline"];
    }
    if (options.loop) {
      _this.mediaConfig.loop = "loop";
    }
    if (options.autoplayMuted && !Object.prototype.hasOwnProperty.call(_this.mediaConfig, "muted")) {
      _this.mediaConfig.muted = true;
    }
    _this.media = options.mediaEl instanceof HTMLMediaElement ? options.mediaEl : typeof options.mediaEl === "function" ? options.mediaEl(_this.mediaConfig) : util.createDom(_this.mediaConfig.mediaType, "", _this.mediaConfig, "");
    if (options.defaultPlaybackRate) {
      _this.media.defaultPlaybackRate = _this.media.playbackRate = options.defaultPlaybackRate;
    }
    if (util.typeOf(options.volume) === "Number") {
      _this.volume = options.volume;
    }
    if (options.autoplayMuted) {
      _this.media.muted = true;
      _this._lastMuted = true;
    }
    if (options.autoplay) {
      _this.media.autoplay = true;
    }
    if (options.playsinline) {
      _this.media.playsinline = true;
    }
    _this._interval = {};
    _this.mediaEventMiddleware = {};
    _this.attachVideoEvents();
    return _this;
  }
  _createClass(MediaProxy2, [{
    key: "setEventsMiddleware",
    value: function setEventsMiddleware(middlewares) {
      var _this2 = this;
      Object.keys(middlewares).map(function(key) {
        _this2.mediaEventMiddleware[key] = middlewares[key];
      });
    }
  }, {
    key: "removeEventsMiddleware",
    value: function removeEventsMiddleware(middlewares) {
      var _this3 = this;
      Object.keys(middlewares).map(function(key) {
        delete _this3.mediaEventMiddleware[key];
      });
    }
  }, {
    key: "attachVideoEvents",
    value: function attachVideoEvents() {
      var _this4 = this;
      var media = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : this.media;
      if (!this._evHandlers) {
        this._evHandlers = VIDEO_EVENTS.map(function(eventKey) {
          var funName = "on".concat(eventKey.charAt(0).toUpperCase()).concat(eventKey.slice(1));
          if (typeof _this4[funName] === "function") {
            _this4.on(eventKey, _this4[funName]);
          }
          return _defineProperty({}, eventKey, getVideoEventHandler(eventKey, _this4));
        });
      }
      this._evHandlers.forEach(function(item) {
        var eventKey = Object.keys(item)[0];
        media.addEventListener(eventKey, item[eventKey], false);
      });
    }
  }, {
    key: "detachVideoEvents",
    value: function detachVideoEvents() {
      var _this5 = this;
      var media = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : this.media;
      this._evHandlers.forEach(function(item) {
        var eventKey = Object.keys(item)[0];
        media.removeEventListener(eventKey, item[eventKey], false);
      });
      this._evHandlers.forEach(function(item) {
        var eventKey = Object.keys(item)[0];
        var funName = "on".concat(eventKey.charAt(0).toUpperCase()).concat(eventKey.slice(1));
        if (typeof _this5[funName] === "function") {
          _this5.off(eventKey, _this5[funName]);
        }
      });
      this._evHandlers = null;
    }
  }, {
    key: "_attachSourceEvents",
    value: function _attachSourceEvents(video, urls) {
      var _this6 = this;
      video.removeAttribute("src");
      video.load();
      urls.forEach(function(item, index) {
        _this6.media.appendChild(util.createDom("source", "", {
          src: "".concat(item.src),
          type: "".concat(item.type || ""),
          "data-index": index + 1
        }));
      });
      var _c = video.children;
      if (!_c) {
        return;
      }
      this._videoSourceCount = _c.length;
      this._videoSourceIndex = _c.length;
      this._vLoadeddata = function(e) {
        _this6.emit(SOURCE_SUCCESS, {
          src: e.target.currentSrc,
          host: util.getHostFromUrl(e.target.currentSrc)
        });
      };
      var _eHandler = null;
      for (var i = 0; i < this._evHandlers.length; i++) {
        if (Object.keys(this._evHandlers[i])[0] === "error") {
          _eHandler = this._evHandlers[i];
          break;
        }
      }
      !this._sourceError && (this._sourceError = function(e) {
        var _dIndex = parseInt(e.target.getAttribute("data-index"), 10);
        _this6._videoSourceIndex--;
        if (_this6._videoSourceIndex === 0 || _dIndex >= _this6._videoSourceCount) {
          var _err = {
            code: 4,
            message: "sources_load_error"
          };
          _eHandler ? _eHandler.error(e, _err) : _this6.errorHandler("error", _err);
        }
        var type = ERROR_TYPE_MAP[4];
        _this6.emit(SOURCE_ERROR, new Errors(_this6, {
          errorType: type,
          errorCode: 4,
          errorMessage: "sources_load_error",
          mediaError: {
            code: 4,
            message: "sources_load_error"
          },
          src: e.target.src
        }));
      });
      for (var _i = 0; _i < _c.length; _i++) {
        _c[_i].addEventListener("error", this._sourceError);
      }
      video.addEventListener("loadeddata", this._vLoadeddata);
    }
  }, {
    key: "_detachSourceEvents",
    value: function _detachSourceEvents(video) {
      var _c = video.children;
      if (!_c || _c.length === 0 || !this._sourceError) {
        return;
      }
      for (var i = 0; i < _c.length; i++) {
        _c[i].removeEventListener("error", this._sourceError);
      }
      while (_c.length > 0) {
        video.removeChild(_c[0]);
      }
      this._vLoadeddata && video.removeEventListener("loadeddata", this._vLoadeddata);
    }
  }, {
    key: "errorHandler",
    value: function errorHandler(name) {
      var error = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : null;
      if (this.media && (this.media.error || error)) {
        var _e = this.media.error || error;
        var type = _e.code ? ERROR_TYPE_MAP[_e.code] : "other";
        var message = _e.message;
        if (!(this.media.currentSrc || this.media.srcObject)) {
          message = "empty_src";
          _e = {
            code: 6,
            message
          };
        }
        this.emit(name, new Errors(this, {
          errorType: type,
          errorCode: _e.code,
          errorMessage: _e.message || "",
          mediaError: _e
        }));
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      if (this.media) {
        if (this.media.pause) {
          this.media.pause();
          this.media.muted = true;
        }
        this.media.removeAttribute("src");
        this.media.load();
      }
      this._currentTime = 0;
      this._duration = 0;
      this.mediaConfig = null;
      for (var k in this._interval) {
        if (Object.prototype.hasOwnProperty.call(this._interval, k)) {
          clearInterval(this._interval[k]);
          this._interval[k] = null;
        }
      }
      this.detachVideoEvents();
      this.media = null;
      this.mediaEventMiddleware = {};
      this.removeAllListeners();
    }
  }, {
    key: "video",
    get: function get() {
      return this.media;
    },
    set: function set(media) {
      this.media = media;
    }
  }, {
    key: "play",
    value: function play() {
      var ret = this.media ? this.media.play() : null;
      return ret;
    }
  }, {
    key: "pause",
    value: function pause() {
      this.media && this.media.pause();
    }
  }, {
    key: "load",
    value: function load() {
      this.media && this.media.load();
    }
  }, {
    key: "canPlayType",
    value: function canPlayType(type) {
      return this.media ? this.media.canPlayType(type) : false;
    }
  }, {
    key: "getBufferedRange",
    value: function getBufferedRange(buffered) {
      var range = [0, 0];
      if (!this.media) {
        return range;
      }
      if (!buffered) {
        buffered = this.media.buffered;
      }
      var currentTime = this.media.currentTime;
      if (buffered) {
        for (var i = 0, len = buffered.length; i < len; i++) {
          range[0] = buffered.start(i);
          range[1] = buffered.end(i);
          if (range[0] <= currentTime && currentTime <= range[1]) {
            break;
          }
        }
      }
      if (range[0] - currentTime <= 0 && currentTime - range[1] <= 0) {
        return range;
      } else {
        return [0, 0];
      }
    }
  }, {
    key: "autoplay",
    get: function get() {
      return this.media ? this.media.autoplay : false;
    },
    set: function set(isTrue) {
      this.media && (this.media.autoplay = isTrue);
    }
  }, {
    key: "buffered",
    get: function get() {
      return this.media ? this.media.buffered : null;
    }
  }, {
    key: "buffered2",
    get: function get() {
      return this.media && this.media.buffered ? util.getBuffered2(this.media.buffered) : null;
    }
  }, {
    key: "bufferedPoint",
    get: function get() {
      var ret = {
        start: 0,
        end: 0
      };
      if (!this.media) {
        return ret;
      }
      var _buffered = this.media.buffered;
      if (!_buffered || _buffered.length === 0) {
        return ret;
      }
      for (var i = 0; i < _buffered.length; i++) {
        if ((_buffered.start(i) <= this.currentTime || _buffered.start(i) < 0.1) && _buffered.end(i) >= this.currentTime) {
          return {
            start: _buffered.start(i),
            end: _buffered.end(i)
          };
        }
      }
      return ret;
    }
  }, {
    key: "crossOrigin",
    get: function get() {
      return this.media ? this.media.crossOrigin : "";
    },
    set: function set(isTrue) {
      this.media && (this.media.crossOrigin = isTrue);
    }
  }, {
    key: "currentSrc",
    get: function get() {
      return this.media ? this.media.currentSrc : "";
    },
    set: function set(src) {
      this.media && (this.media.currentSrc = src);
    }
  }, {
    key: "currentTime",
    get: function get() {
      if (!this.media) {
        return 0;
      }
      return this.media.currentTime !== void 0 ? this.media.currentTime : this._currentTime;
    },
    set: function set(time) {
      this.media && (this.media.currentTime = time);
    }
  }, {
    key: "defaultMuted",
    get: function get() {
      return this.media ? this.media.defaultMuted : false;
    },
    set: function set(isTrue) {
      this.media && (this.media.defaultMuted = isTrue);
    }
  }, {
    key: "duration",
    get: function get() {
      return this._duration;
    }
  }, {
    key: "ended",
    get: function get() {
      return this.media ? this.media.ended : false;
    }
  }, {
    key: "error",
    get: function get() {
      return this.media.error;
    }
  }, {
    key: "errorNote",
    get: function get() {
      var err = this.media.error;
      if (!err) {
        return "";
      }
      var status = ["MEDIA_ERR_ABORTED", "MEDIA_ERR_NETWORK", "MEDIA_ERR_DECODE", "MEDIA_ERR_SRC_NOT_SUPPORTED"];
      return status[this.media.error.code - 1];
    }
  }, {
    key: "loop",
    get: function get() {
      return this.media ? this.media.loop : false;
    },
    set: function set(isTrue) {
      this.media && (this.media.loop = isTrue);
    }
  }, {
    key: "muted",
    get: function get() {
      return this.media ? this.media.muted : false;
    },
    set: function set(isTrue) {
      if (!this.media || this.media.muted === isTrue) {
        return;
      }
      this._lastMuted = this.media.muted;
      this.media.muted = isTrue;
    }
  }, {
    key: "networkState",
    get: function get() {
      return this.media.networkState;
    }
  }, {
    key: "paused",
    get: function get() {
      return this.media ? this.media.paused : true;
    }
  }, {
    key: "playbackRate",
    get: function get() {
      return this.media ? this.media.playbackRate : 0;
    },
    set: function set(rate) {
      if (!this.media || rate === Infinity) {
        return;
      }
      this.media.defaultPlaybackRate = rate;
      this.media.playbackRate = rate;
    }
  }, {
    key: "played",
    get: function get() {
      return this.media ? this.media.played : null;
    }
  }, {
    key: "preload",
    get: function get() {
      return this.media ? this.media.preload : false;
    },
    set: function set(isTrue) {
      this.media && (this.media.preload = isTrue);
    }
  }, {
    key: "readyState",
    get: function get() {
      return this.media.readyState;
    }
  }, {
    key: "seekable",
    get: function get() {
      return this.media ? this.media.seekable : false;
    }
  }, {
    key: "seeking",
    get: function get() {
      return this.media ? this.media.seeking : false;
    }
  }, {
    key: "src",
    get: function get() {
      return this.media ? this.media.src : "";
    },
    set: function set(url) {
      if (!this.media) {
        return;
      }
      this.emit(URL_CHANGE, url);
      this.emit(WAITING);
      this._currentTime = 0;
      this._duration = 0;
      if (util.isMSE(this.media)) {
        this.onWaiting();
        return;
      }
      this._detachSourceEvents(this.media);
      if (util.typeOf(url) === "Array") {
        this._attachSourceEvents(this.media, url);
      } else if (url) {
        this.media.src = url;
      } else {
        this.media.removeAttribute("src");
      }
      this.load();
    }
  }, {
    key: "volume",
    get: function get() {
      return this.media ? this.media.volume : 0;
    },
    set: function set(vol) {
      if (vol === Infinity || !this.media) {
        return;
      }
      this.media.volume = vol;
    }
  }, {
    key: "aspectRatio",
    get: function get() {
      return this.media ? this.media.videoWidth / this.media.videoHeight : 0;
    }
  }, {
    key: "addInnerOP",
    value: function addInnerOP(event) {
      this._internalOp[event] = true;
    }
  }, {
    key: "removeInnerOP",
    value: function removeInnerOP(event) {
      delete this._internalOp[event];
    }
  }, {
    key: "emit",
    value: function emit(event, data) {
      var _get2;
      for (var _len = arguments.length, args = new Array(_len > 2 ? _len - 2 : 0), _key = 2; _key < _len; _key++) {
        args[_key - 2] = arguments[_key];
      }
      (_get2 = _get(_getPrototypeOf(MediaProxy2.prototype), "emit", this)).call.apply(_get2, [this, event, data].concat(args));
    }
  }, {
    key: "on",
    value: function on(event, callback) {
      var _get3;
      for (var _len2 = arguments.length, args = new Array(_len2 > 2 ? _len2 - 2 : 0), _key2 = 2; _key2 < _len2; _key2++) {
        args[_key2 - 2] = arguments[_key2];
      }
      (_get3 = _get(_getPrototypeOf(MediaProxy2.prototype), "on", this)).call.apply(_get3, [this, event, callback].concat(args));
    }
  }, {
    key: "once",
    value: function once(event, callback) {
      var _get4;
      for (var _len3 = arguments.length, args = new Array(_len3 > 2 ? _len3 - 2 : 0), _key3 = 2; _key3 < _len3; _key3++) {
        args[_key3 - 2] = arguments[_key3];
      }
      (_get4 = _get(_getPrototypeOf(MediaProxy2.prototype), "once", this)).call.apply(_get4, [this, event, callback].concat(args));
    }
  }, {
    key: "off",
    value: function off(event, callback) {
      var _get5;
      for (var _len4 = arguments.length, args = new Array(_len4 > 2 ? _len4 - 2 : 0), _key4 = 2; _key4 < _len4; _key4++) {
        args[_key4 - 2] = arguments[_key4];
      }
      (_get5 = _get(_getPrototypeOf(MediaProxy2.prototype), "off", this)).call.apply(_get5, [this, event, callback].concat(args));
    }
  }, {
    key: "offAll",
    value: function offAll() {
      _get(_getPrototypeOf(MediaProxy2.prototype), "removeAllListeners", this).call(this);
    }
  }]);
  return MediaProxy2;
}(EventEmitter);
export { MediaProxy as default };
