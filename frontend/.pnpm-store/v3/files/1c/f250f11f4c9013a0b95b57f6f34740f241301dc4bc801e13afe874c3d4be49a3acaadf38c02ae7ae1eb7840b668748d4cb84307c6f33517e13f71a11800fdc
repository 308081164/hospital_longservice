import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import sniffer from "../../utils/sniffer.js";
import { DURATION_CHANGE } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
import initDotsAPI from "./dotsApi.js";
var CALLBACK_MAP = {
  dragmove: "onProgressMove",
  dragstart: "onProgressDragStart",
  dragend: "onProgressDragEnd",
  click: "onProgressClick",
  mouseover: "onProgressMouseOver",
  mouseenter: "onProgressMouseOver"
};
var ProgressPreview = /* @__PURE__ */ function(_Plugin) {
  _inherits(ProgressPreview2, _Plugin);
  var _super = _createSuper(ProgressPreview2);
  function ProgressPreview2(args) {
    var _this;
    _classCallCheck(this, ProgressPreview2);
    _this = _super.call(this, args);
    _defineProperty(_assertThisInitialized(_this), "onMousemove", function(e) {
      if (_this.config.disable) {
        return;
      }
      if (util.hasClass(e.target, "xg-spot-content") && _this.config.isHideThumbnailHover) {
        _this.player.plugins.progress.onMouseLeave(e);
        return;
      }
      if (_this._state.f || util.hasClass(e.target, "xg-spot-content")) {
        util.event(e);
        e.stopPropagation();
      }
    });
    _defineProperty(_assertThisInitialized(_this), "onMousedown", function(e) {
      if (_this.config.disable) {
        return;
      }
      if (_this._state.f || util.hasClass(e.target, "xg-spot-content")) {
        util.event(e);
        e.stopPropagation();
      }
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseup", function(e) {
      if (!_this.isDrag) {
        return;
      }
      var progress = _this.player.plugins.progress;
      if (progress && progress.pos) {
        progress.onMouseUp(e);
        !progress.pos.isEnter && progress.onMouseLeave(e);
      }
    });
    _defineProperty(_assertThisInitialized(_this), "onDotMouseLeave", function(e) {
      if (_this.config.disable) {
        return;
      }
      _this._curDot.removeEventListener("mouseleave", _this.onDotMouseLeave);
      _this.blurDot(e.target);
      _this._curDot = null;
      var progress = _this.player.plugins.progress;
      progress && progress.enableBlur();
      _this.show();
    });
    _defineProperty(_assertThisInitialized(_this), "onProgressMouseOver", function(data, e) {
      if (_this.config.disable) {
        return;
      }
      if (util.hasClass(e.target, "xgplayer-spot") && !_this._curDot) {
        _this._curDot = e.target;
        _this.focusDot(e.target);
        if (_this._curDot.children.length > 0) {
          _this.hide();
        }
        var progress = _this.player.plugins.progress;
        progress && progress.disableBlur();
        _this._curDot.addEventListener("mouseleave", _this.onDotMouseLeave);
      }
    });
    _this._ispots = [];
    _this.videoPreview = null;
    _this.videothumbnail = null;
    _this.thumbnail = null;
    _this.timeStr = "";
    _this._state = {
      now: 0,
      f: false
    };
    return _this;
  }
  _createClass(ProgressPreview2, [{
    key: "beforeCreate",
    value: function beforeCreate(args) {
      var progress = args.player.plugins.progress;
      if (progress) {
        args.root = progress.root;
      }
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      this._curDot = null;
      this.handlerSpotClick = this.hook("spotClick", function(_event, data) {
        if (data.seekTime) {
          _this2.player.seek(data.seekTime);
        }
      });
      this.transformTimeHook = this.hook("transformTime", function(time) {
        _this2.setTimeContent(util.format(time));
      });
      initDotsAPI(this);
      this.on(DURATION_CHANGE, function() {
        _this2.show();
      });
      if (this.config.disable) {
        this.disable();
      }
      this.extTextRoot = this.find(".xg-spot-ext-text");
    }
  }, {
    key: "setConfig",
    value: function setConfig(config) {
      var _this3 = this;
      if (!config) {
        return;
      }
      Object.keys(config).map(function(key) {
        _this3.config[key] = config[key];
      });
    }
  }, {
    key: "onPluginsReady",
    value: function onPluginsReady() {
      var player = this.player;
      if (!player.plugins.progress) {
        return;
      }
      this.previewLine = this.find(".xg-spot-line");
      this.timePoint = this.find(".xgplayer-progress-point");
      this.timeText = this.find(".xg-spot-time");
      this.tipText = this.find(".spot-inner-text");
      this._hasThumnail = false;
      this.registerThumbnail();
      this.bindEvents();
    }
  }, {
    key: "bindEvents",
    value: function bindEvents() {
      var _this4 = this;
      var progress = this.player.plugins.progress;
      if (!progress) {
        return;
      }
      Object.keys(CALLBACK_MAP).map(function(key) {
        _this4[CALLBACK_MAP[key]] = _this4[CALLBACK_MAP[key]].bind(_this4);
        progress.addCallBack(key, _this4[CALLBACK_MAP[key]]);
      });
      if (sniffer.device === "mobile")
        return;
      this.bind(".xg-spot-info", "mousemove", this.onMousemove);
      this.bind(".xg-spot-info", "mousedown", this.onMousedown);
      this.bind(".xg-spot-info", "mouseup", this.onMouseup);
      var fun = this.hook("previewClick", function() {
      });
      this.handlerPreviewClick = function(e) {
        e.stopPropagation();
        fun(parseInt(_this4._state.now * 1e3, 10) / 1e3, e);
        if (progress) {
          progress.onMouseUp(e);
        }
      };
      this.bind(".xg-spot-content", "mouseup", this.handlerPreviewClick);
    }
  }, {
    key: "onProgressMove",
    value: function onProgressMove(data, e) {
      if (this.config.disable || !this.player.duration) {
        return;
      }
      this.updatePosition(data.offset, data.width, data.currentTime, data.e);
    }
  }, {
    key: "onProgressDragStart",
    value: function onProgressDragStart(data) {
      if (this.config.disable || !this.player.duration) {
        return;
      }
      this.isDrag = true;
      this.videoPreview && util.addClass(this.videoPreview, "show");
    }
  }, {
    key: "onProgressDragEnd",
    value: function onProgressDragEnd(data) {
      if (this.config.disable || !this.player.duration) {
        return;
      }
      this.isDrag = false;
      this.videoPreview && util.removeClass(this.videoPreview, "show");
    }
  }, {
    key: "onProgressClick",
    value: function onProgressClick(data, e) {
      if (this.config.disable) {
        return;
      }
      if (util.hasClass(e.target, "xgplayer-spot")) {
        e.stopPropagation();
        e.preventDefault();
        ["time", "id", "text"].map(function(key) {
          data[key] = e.target.getAttribute("data-".concat(key));
        });
        data.time && (data.time = Number(data.time));
        this.handlerSpotClick(e, data);
      }
    }
  }, {
    key: "updateLinePos",
    value: function updateLinePos(offset, cwidth) {
      var root = this.root, previewLine = this.previewLine, player = this.player, config = this.config;
      var mode = player.controls.mode;
      var isflex = mode === "flex";
      var lwidth = root.getBoundingClientRect().width;
      if (!lwidth && this._hasThumnail) {
        return;
      }
      lwidth = this._hasThumnail && lwidth < config.width ? config.width : lwidth;
      var x = offset - lwidth / 2;
      var _t;
      if (x < 0 && !isflex) {
        x = 0;
        _t = offset - lwidth / 2;
      } else if (x > cwidth - lwidth && !isflex) {
        _t = x - (cwidth - lwidth);
        x = cwidth - lwidth;
      } else {
        _t = 0;
      }
      _t !== void 0 && (previewLine.style.transform = "translateX(".concat(_t.toFixed(2), "px)"));
      root.style.transform = "translateX(".concat(x.toFixed(2), "px) translateZ(0)");
    }
  }, {
    key: "updateTimeText",
    value: function updateTimeText(timeStr) {
      var timeText = this.timeText, timePoint = this.timePoint;
      timeText.innerHTML = timeStr;
      !this.thumbnail && (timePoint.innerHTML = timeStr);
    }
  }, {
    key: "updatePosition",
    value: function updatePosition(offset, cwidth, time, e) {
      var root = this.root, config = this.config, _state = this._state;
      if (!root) {
        return;
      }
      _state.now = time;
      this.transformTimeHook(time);
      var timeStr = this.timeStr;
      if (e && e.target && util.hasClass(e.target, "xgplayer-spot")) {
        this.showTips(e.target.getAttribute("data-text"), false, timeStr);
        this.focusDot(e.target);
        _state.f = true;
        config.isFocusDots && _state.f && (_state.now = parseInt(e.target.getAttribute("data-time"), 10));
      } else if (config.defaultText) {
        _state.f = false;
        this.showTips(config.defaultText, true, timeStr);
      } else {
        _state.f = false;
        this.hideTips("");
      }
      this.updateTimeText(timeStr);
      this.updateThumbnails(_state.now);
      this.updateLinePos(offset, cwidth);
    }
  }, {
    key: "setTimeContent",
    value: function setTimeContent(str) {
      this.timeStr = str;
    }
  }, {
    key: "updateThumbnails",
    value: function updateThumbnails(time) {
      var player = this.player, videoPreview = this.videoPreview, config = this.config;
      var thumbnail = player.plugins.thumbnail;
      if (thumbnail && thumbnail.usable) {
        this.thumbnail && thumbnail.update(this.thumbnail, time, config.width, config.height);
        var rect = videoPreview && videoPreview.getBoundingClientRect();
        this.videothumbnail && thumbnail.update(this.videothumbnail, time, rect.width, rect.height);
      }
    }
  }, {
    key: "registerThumbnail",
    value: function registerThumbnail() {
      var thumbnailConfig = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {};
      if (sniffer.device === "mobile") {
        return;
      }
      var player = this.player, config = this.config;
      var thumbnail = player.getPlugin("thumbnail");
      if (thumbnail) {
        thumbnail.setConfig(thumbnailConfig);
      }
      if (!thumbnail || !thumbnail.usable || !config.isShowThumbnail) {
        util.addClass(this.root, "short-line no-thumbnail");
        return;
      } else {
        util.removeClass(this.root, "short-line no-thumbnail");
      }
      if (config.mode === "short") {
        util.addClass(this.root, "short-line");
      }
      this._hasThumnail = true;
      var tRoot = this.find(".xg-spot-thumbnail");
      this.thumbnail = thumbnail.createThumbnail(tRoot, "progress-thumbnail");
      if (config.isShowCoverPreview) {
        this.videoPreview = util.createDom("xg-video-preview", "", {}, "xgvideo-preview");
        player.root.appendChild(this.videoPreview);
        this.videothumbnail = thumbnail.createThumbnail(this.videoPreview, "xgvideo-thumbnail");
      }
      this.updateThumbnails(0);
    }
  }, {
    key: "calcuPosition",
    value: function calcuPosition(time, duration) {
      var progress = this.player.plugins.progress;
      var player = this.player;
      var totalWidth = progress.root.getBoundingClientRect().width;
      var widthPerSeconds = player.duration / totalWidth * 6;
      if (time + duration > player.duration) {
        duration = player.duration - time;
      }
      time / player.duration * 100;
      duration / player.duration;
      return {
        left: time / player.duration * 100,
        width: duration / player.duration * 100,
        isMini: duration < widthPerSeconds
      };
    }
  }, {
    key: "showDot",
    value: function showDot(id) {
      var dot = this.findDot(id);
      if (dot) {
        var rect = this.root.getBoundingClientRect();
        var width = rect.width;
        var offset = dot.time / this.player.duration * width;
        this.updatePosition(offset, width, dot.time);
      }
    }
  }, {
    key: "focusDot",
    value: function focusDot(target, id) {
      if (!target) {
        return;
      }
      if (!id) {
        id = target.getAttribute("data-id");
      }
      util.addClass(target, "active");
      this._activeDotId = id;
    }
  }, {
    key: "blurDot",
    value: function blurDot(target) {
      if (!target) {
        var id = this._activeDotId;
        target = this.getDotDom(id);
      }
      if (!target) {
        return;
      }
      util.removeClass(target, "active");
      this._activeDotId = null;
    }
  }, {
    key: "showTips",
    value: function showTips(text, isDefault) {
      var timeStr = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : "";
      util.addClass(this.root, "no-timepoint");
      if (!text) {
        return;
      }
      util.addClass(this.find(".xg-spot-content"), "show-text");
      if (isDefault && this.config.mode === "production") {
        util.addClass(this.root, "product");
        this.tipText.textContent = text;
      } else {
        util.removeClass(this.root, "product");
        this.tipText.textContent = this._hasThumnail ? text : "".concat(timeStr, " ").concat(text);
      }
    }
  }, {
    key: "hideTips",
    value: function hideTips() {
      util.removeClass(this.root, "no-timepoint");
      this.tipText.textContent = "";
      util.removeClass(this.find(".xg-spot-content"), "show-text");
      util.removeClass(this.root, "product");
    }
  }, {
    key: "hide",
    value: function hide() {
      util.addClass(this.root, "hide");
    }
  }, {
    key: "show",
    value: function show(value) {
      util.removeClass(this.root, "hide");
    }
  }, {
    key: "enable",
    value: function enable() {
      var config = this.config, playerConfig = this.playerConfig;
      this.config.disable = false;
      this.show();
      if (!this.thumbnail && config.isShowThumbnail) {
        this.registerThumbnail(playerConfig.thumbnail || {});
      }
    }
  }, {
    key: "disable",
    value: function disable() {
      this.config.disable = true;
      this.hide();
    }
  }, {
    key: "destroy",
    value: function destroy() {
      var _this5 = this;
      var progress = this.player.plugins.progress;
      progress && Object.keys(CALLBACK_MAP).map(function(key) {
        progress.removeCallBack(key, _this5[CALLBACK_MAP[key]]);
      });
      this.videothumbnail = null;
      this.thumbnail = null;
      this.videoPreview && this.player.root.removeChild(this.videoPreview);
      this.unbind(".xg-spot-info", "mousemove", this.onMousemove);
      this.unbind(".xg-spot-info", "mousedown", this.onMousedown);
      this.unbind(".xg-spot-info", "mouseup", this.onMouseup);
      this.unbind(".xg-spot-content", "mouseup", this.handlerPreviewClick);
    }
  }, {
    key: "render",
    value: function render() {
      if (sniffer.device === "mobile" || this.playerConfig.isMobileSimulateMode === "mobile") {
        return "";
      }
      return '<div class="xg-spot-info hide '.concat(this.config.mode === "short" ? "short-line" : "", '">\n      <div class="xg-spot-content">\n        <div class="xg-spot-thumbnail">\n          <span class="xg-spot-time"></span>\n        </div>\n        <div class="xg-spot-text"><span class="spot-inner-text"></span></div>\n      </div>\n      <div class="xgplayer-progress-point">00:00</div>\n      <div class="xg-spot-ext-text"></div>\n      <div class="xg-spot-line"></div>\n    </div>');
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "progresspreview";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        index: 1,
        miniWidth: 6,
        ispots: [],
        defaultText: "",
        isFocusDots: true,
        isHideThumbnailHover: true,
        isShowThumbnail: true,
        isShowCoverPreview: false,
        mode: "",
        disable: false,
        width: 160,
        height: 90
      };
    }
  }]);
  return ProgressPreview2;
}(Plugin);
export { ProgressPreview as default };
