import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { DURATION_CHANGE } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var Thumbnail = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(Thumbnail2, _BasePlugin);
  var _super = _createSuper(Thumbnail2);
  function Thumbnail2(args) {
    var _this;
    _classCallCheck(this, Thumbnail2);
    _this = _super.call(this, args);
    _this.ratio = 1;
    _this.interval = null;
    _this._preloadMark = {};
    return _this;
  }
  _createClass(Thumbnail2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      if (this.usable) {
        this.initThumbnail();
      }
      this.on([DURATION_CHANGE], function() {
        var _this2$config = _this2.config, pic_num = _this2$config.pic_num, interval = _this2$config.interval;
        _this2.usable && (_this2.interval = interval > 0 ? interval : Math.round(_this2.player.duration * 1e3 / pic_num) / 1e3);
      });
    }
  }, {
    key: "setConfig",
    value: function setConfig(config) {
      var _this3 = this;
      if (!config) {
        return;
      }
      var keys = Object.keys(config);
      if (keys.length < 1) {
        return;
      }
      keys.forEach(function(key) {
        _this3.config[key] = config[key];
      });
      this.usable && this.initThumbnail();
    }
  }, {
    key: "usable",
    get: function get() {
      var _this$config = this.config, urls = _this$config.urls, pic_num = _this$config.pic_num;
      return urls && urls.length > 0 && pic_num > 0;
    }
  }, {
    key: "initThumbnail",
    value: function initThumbnail() {
      var _this$config2 = this.config, width = _this$config2.width, height = _this$config2.height, pic_num = _this$config2.pic_num, interval = _this$config2.interval;
      this.ratio = width / height * 100;
      this.interval = interval || Math.round(this.player.duration / pic_num);
      this._preloadMark = {};
    }
  }, {
    key: "getUrlByIndex",
    value: function getUrlByIndex(index) {
      return index >= 0 && index < this.config.urls.length ? this.config.urls[index] : "";
    }
  }, {
    key: "preload",
    value: function preload(index) {
      var _this4 = this;
      if (this._preloadMark[index]) {
        return;
      }
      var urls = this.config.urls;
      var len = urls.length;
      var arr = [];
      index > 0 && arr.push(index - 1);
      arr.push(index);
      index > 0 && index < len - 1 && arr.push(index + 1);
      arr.map(function(item) {
        if (!_this4._preloadMark[item] && item >= 0 && item < len) {
          _this4._preloadMark[item] = 1;
          util.preloadImg(urls[item], function() {
            _this4._preloadMark[item] = 2;
          });
        }
      });
    }
  }, {
    key: "getPosition",
    value: function getPosition(now) {
      var containerWidth = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : 0;
      var containerHeight = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : 0;
      var _this$config3 = this.config, pic_num = _this$config3.pic_num, row = _this$config3.row, col = _this$config3.col, width = _this$config3.width, height = _this$config3.height;
      this.interval = Math.round(this.player.duration / pic_num);
      var index = Math.ceil(now / this.interval);
      index = index > pic_num ? pic_num : index;
      var urlIndex = index < row * col ? 0 : Math.ceil(index / (row * col)) - 1;
      var indexInPage = index - urlIndex * (col * row);
      var rowIndex = indexInPage > 0 ? Math.ceil(indexInPage / col) - 1 : 0;
      var colIndex = indexInPage > 0 ? indexInPage - rowIndex * col - 1 : 0;
      var swidth = 0;
      var sHeight = 0;
      if (containerWidth && containerHeight) {
        var per = containerWidth / containerHeight;
        if (per < width / height) {
          sHeight = containerHeight;
          swidth = sHeight * (width / height);
        } else {
          swidth = containerWidth;
          sHeight = swidth / (width / height);
        }
      } else if (!containerHeight) {
        swidth = containerWidth || width;
        sHeight = swidth / (width / height);
      } else if (!containerWidth) {
        sHeight = containerHeight || height;
        swidth = sHeight * (width / height);
      }
      var url = this.getUrlByIndex(urlIndex);
      return {
        urlIndex,
        rowIndex,
        colIndex,
        url,
        height: sHeight,
        width: swidth,
        style: {
          backgroundImage: "url(".concat(url, ")"),
          backgroundSize: "".concat(swidth * col, "px auto"),
          backgroundPosition: "-".concat(colIndex * swidth, "px -").concat(rowIndex * sHeight, "px"),
          width: "".concat(swidth, "px"),
          height: "".concat(sHeight, "px")
        }
      };
    }
  }, {
    key: "update",
    value: function update(dom, now) {
      var containerWidth = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : 0;
      var containerHeight = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : 0;
      var customStyle = arguments.length > 4 && arguments[4] !== void 0 ? arguments[4] : "";
      var _this$config4 = this.config, pic_num = _this$config4.pic_num, urls = _this$config4.urls;
      if (pic_num <= 0 || !urls || urls.length === 0) {
        return;
      }
      var pos = this.getPosition(now, containerWidth, containerHeight);
      this.preload(pos.urlIndex);
      Object.keys(pos.style).map(function(key) {
        dom.style[key] = pos.style[key];
      });
      Object.keys(customStyle).map(function(key) {
        dom.style[key] = customStyle[key];
      });
    }
  }, {
    key: "changeConfig",
    value: function changeConfig() {
      var newConfig = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {};
      this.setConfig(newConfig);
    }
  }, {
    key: "createThumbnail",
    value: function createThumbnail(root, className) {
      var dom = util.createDom("xg-thumbnail", "", {}, "thumbnail ".concat(className));
      root && root.appendChild(dom);
      return dom;
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "thumbnail";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        isShow: false,
        urls: [],
        pic_num: 0,
        col: 0,
        row: 0,
        height: 90,
        width: 160,
        scale: 1,
        className: "",
        hidePortrait: false
      };
    }
  }]);
  return Thumbnail2;
}(Plugin);
export { Thumbnail as default };
