import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, objectSpread2 as _objectSpread2, get as _get, getPrototypeOf as _getPrototypeOf } from "../../_virtual/_rollupPluginBabelHelpers.js";
import SubTitles from "xgplayer-subtitles";
import util from "../../utils/util.js";
import { PLAYER_FOCUS, PLAYER_BLUR } from "../../events.js";
import "../../utils/debug.js";
import { POSITIONS } from "../../plugin/plugin.js";
import OptionsIcon from "../common/optionsIcon.js";
import NativeSubTitle from "./nativeSubTitle.js";
var DEFAULT_TYPE = {
  CLOSE: "close",
  OPEN: "open",
  TEXT_CLOSE: "text-close"
};
function formatList(list) {
  var defaultIndex = -1;
  list.forEach(function(item, index) {
    if (!item.id && !item.language) {
      item.id = index;
    }
    item.id = String(item.id);
    !item.url && (item.url = item.src);
    !item.text && (item.text = item.label);
    !item.language && (item.language = item.srclang);
    item.isDefault === void 0 && (item.isDefault = item.default || false);
    if (item.isDefault || item.default) {
      if (defaultIndex < 0) {
        defaultIndex = index;
      } else {
        item.isDefault = item.default = false;
      }
    }
  });
  return defaultIndex;
}
function checkIsSame(src, dist) {
  var isIdS = util.isNotNull(src.id) && util.isNotNull(dist.id) && src.id === dist.id;
  var isLS = util.isNotNull(src.language) && util.isNotNull(dist.language) && src.language === dist.language;
  return isIdS || isLS;
}
var TextTrack = /* @__PURE__ */ function(_OptionsIcon) {
  _inherits(TextTrack2, _OptionsIcon);
  var _super = _createSuper(TextTrack2);
  function TextTrack2() {
    var _this;
    _classCallCheck(this, TextTrack2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "_onOff", function() {
      _this.switchOffSubtitle();
    });
    _defineProperty(_assertThisInitialized(_this), "_onChange", function(data) {
      var _curIndex = _this.getSubTitleIndex(_this.config.list, data);
      if (_curIndex < 0) {
        return;
      }
      _this.updateCurItem(_curIndex, data);
    });
    _defineProperty(_assertThisInitialized(_this), "_onListReset", function(data) {
      _this.updateList(data);
    });
    _defineProperty(_assertThisInitialized(_this), "clickSwitch", function(e, data) {
      var isActionClose = data.type === DEFAULT_TYPE.CLOSE || data.type === DEFAULT_TYPE.TEXT_CLOSE;
      if (_this.subTitles) {
        if (isActionClose) {
          _this.subTitles.switchOff();
        } else {
          _this.switchSubTitle({
            language: data.language,
            id: data.id
          });
        }
      }
    });
    _defineProperty(_assertThisInitialized(_this), "onIconClick", function(e) {
      if (_this.curItem) {
        _this.subTitles.switchOff();
      } else {
        _this.switchOnSubtitle(e);
      }
    });
    _defineProperty(_assertThisInitialized(_this), "onPlayerFocus", function(e) {
      if (!_this.subTitles || !_this.config.style.follow) {
        return;
      }
      _this.rePosition();
    });
    _defineProperty(_assertThisInitialized(_this), "onPlayerBlur", function(e) {
      if (!_this.subTitles || !_this.config.style.follow || _this.playerConfig.marginControls) {
        return;
      }
      _this.subTitles.root && (_this.subTitles.root.style.transform = "translate(0, 0)");
    });
    return _this;
  }
  _createClass(TextTrack2, [{
    key: "beforeCreate",
    value: function beforeCreate(args) {
      var texttrack = args.player.config.texttrack || args.player.config.textTrack;
      if (util.typeOf(texttrack) === "Array") {
        args.config.list = texttrack;
      }
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this$config = this.config, list = _this$config.list, mode = _this$config.mode;
      var defaultIndex = formatList(list);
      _get(_getPrototypeOf(TextTrack2.prototype), "afterCreate", this).call(this);
      this.curIndex = -1;
      this.lastIndex = -1;
      this.curItem = null;
      this._nativeTracks = null;
      this.handlerClickSwitch = this.hook("subtitle_change", this.clickSwitch);
      if (mode === "native") {
        this._initNativeSubtitle();
      } else {
        this._initExtSubTitle(defaultIndex);
      }
    }
  }, {
    key: "_initNativeSubtitle",
    value: function _initNativeSubtitle() {
      var player = this.player;
      if (!player._subTitles) {
        player._subTitles = new NativeSubTitle(player.media);
      }
      this.subTitles = player._subTitles;
      this.subTitles.on("off", this._onOff);
      this.subTitles.on("change", this._onChange);
      this.subTitles.on("reset", this._onListReset);
    }
  }, {
    key: "_initExtSubTitle",
    value: function _initExtSubTitle(defaultIndex) {
      var _this$config2 = this.config, list = _this$config2.list, style = _this$config2.style, isDefaultOpen = _this$config2.isDefaultOpen, updateMode = _this$config2.updateMode, renderMode = _this$config2.renderMode;
      if (isDefaultOpen && defaultIndex < 0 && list.length > 0) {
        defaultIndex = 0;
        list[0].isDefault = true;
      }
      var config = {
        subTitles: list,
        defaultOpen: isDefaultOpen,
        updateMode,
        renderMode,
        debugger: this.config.debugger
      };
      Object.keys(style).map(function(key) {
        config[key] = style[key];
      });
      var _needPos = !this.playerConfig.marginControls && this.player.controls.root;
      if (_needPos) {
        this.on(PLAYER_FOCUS, this.onPlayerFocus);
        this.on(PLAYER_BLUR, this.onPlayerBlur);
      }
      var player = this.player;
      if (!player._subTitles) {
        player._subTitles = new SubTitles(config);
        player._subTitles.attachPlayer(this.player);
      } else {
        player._subTitles._isOpen && (defaultIndex = this.getSubTitleIndex(this.config.list, player._subTitles.currentText));
      }
      this.subTitles = player._subTitles;
      this.subTitles.on("off", this._onOff);
      this.subTitles.on("change", this._onChange);
      this.subTitles.on("reset", this._onListReset);
      if (style.follow && this.subTitles.root) {
        util.addClass(this.subTitles.root, "follow-control");
      }
      this._renderList(list, isDefaultOpen, defaultIndex);
    }
  }, {
    key: "_renderList",
    value: function _renderList(list, isDefaultOpen, defaultIndex) {
      if (!list || list.length === 0) {
        return;
      }
      if (isDefaultOpen) {
        defaultIndex = defaultIndex < 0 ? 0 : defaultIndex;
        list[defaultIndex].isDefault = true;
        this.curIndex = defaultIndex;
        this.curItem = list[defaultIndex];
        this.switchIconState(true);
      } else {
        this.switchIconState(false);
      }
      if (this.player.isCanplay && list.length > 0) {
        this.renderItemList(list);
        this.show();
      }
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        textTrackOpen: {
          icon: "",
          class: "xg-texttrak-open"
        },
        textTrackClose: {
          icon: "",
          class: "xg-texttrak-close"
        }
      };
    }
  }, {
    key: "show",
    value: function show(value) {
      if (!this.config.list || this.config.list.length < 1) {
        return;
      }
      util.addClass(this.root, "show");
    }
  }, {
    key: "getSubTitleIndex",
    value: function getSubTitleIndex(list) {
      var subtitle = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {
        id: "",
        language: ""
      };
      var cIndex = -1;
      if (!subtitle || !subtitle.id && !subtitle.language) {
        return cIndex;
      }
      list.forEach(function(item, index) {
        if (checkIsSame(item, subtitle)) {
          cIndex = index;
        }
      });
      return cIndex;
    }
  }, {
    key: "updateSubtitles",
    value: function updateSubtitles() {
      var list = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : [];
      var needRemove = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : true;
      if (!list) {
        return;
      }
      this.updateList({
        list
      });
      this.subTitles && this.subTitles.setSubTitles(this.config.list, this.curIndex > -1, needRemove);
    }
  }, {
    key: "updateList",
    value: function updateList() {
      var data = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {};
      if (!data.list) {
        return;
      }
      var nList = [];
      data.list.forEach(function(item) {
        nList.push(item);
      });
      var defaultIndex = formatList(nList);
      if (data.isOpen) {
        this.curIndex = defaultIndex;
        this.curItem = defaultIndex > -1 ? nList[defaultIndex] : null;
      } else {
        this.curIndex = -1;
        this.curItem = null;
      }
      this.config.list = nList;
      if (nList.length > 0) {
        this.show();
      } else {
        this.switchOffSubtitle();
        this.hide();
      }
      this.renderItemList();
    }
  }, {
    key: "switchSubTitle",
    value: function switchSubTitle() {
      var subtitle = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {
        id: "",
        language: ""
      };
      this.switchIconState(true);
      var cIndex = this.getSubTitleIndex(this.config.list, subtitle);
      if (cIndex < 0) {
        return;
      }
      this.subTitles.switch(subtitle).catch(function(e) {
      });
    }
  }, {
    key: "switchOffSubtitle",
    value: function switchOffSubtitle(e) {
      this.emit("subtitle_change", {
        off: true,
        isListUpdate: false,
        list: []
      });
      this.lastIndex = this.curIndex;
      this.curIndex = -1;
      this.curItem = null;
      this.switchIconState(false);
      this.renderItemList();
    }
  }, {
    key: "switchOnSubtitle",
    value: function switchOnSubtitle() {
      var list = this.config.list;
      var _sub = this.lastIndex > -1 ? this.lastIndex : 0;
      var _item = list[_sub];
      this.switchIconState(true);
      this.switchSubTitle(_item);
    }
  }, {
    key: "switchIconState",
    value: function switchIconState(isopen) {
      this.setAttr("data-state", isopen ? "open" : "close");
    }
  }, {
    key: "onItemClick",
    value: function onItemClick(e, data) {
      var target = e.delegateTarget;
      var language = target.getAttribute("language");
      var id = target.getAttribute("data-id");
      var type = target.getAttribute("data-type");
      _get(_getPrototypeOf(TextTrack2.prototype), "onItemClick", this).apply(this, arguments);
      this.handlerClickSwitch(e, {
        language,
        id,
        type
      });
    }
  }, {
    key: "changeCurrentText",
    value: function changeCurrentText() {
      if (this.isIcons) {
        return;
      }
      var _this$config3 = this.config, list = _this$config3.list, closeText = _this$config3.closeText;
      var index = this.curIndex;
      if (index - 1 < 0) {
        this.find(".icon-text").innerHTML = this.getTextByLang(closeText, "iconText");
      } else if (index - 1 < list.length) {
        var curItem = list[index - 1];
        if (!curItem)
          return;
        this.find(".icon-text").innerHTML = this.getTextByLang(curItem, "iconText");
      }
    }
  }, {
    key: "updateCurItem",
    value: function updateCurItem(cIndex, subtitle) {
      this.curIndex = cIndex;
      this.curItem = this.config.list[cIndex - 1];
      this.renderItemList();
      this.emit("subtitle_change", _objectSpread2({
        off: false,
        isListUpdate: false,
        list: []
      }, subtitle));
    }
  }, {
    key: "renderItemList",
    value: function renderItemList() {
      var _this2 = this;
      var _this$config4 = this.config, list = _this$config4.list, closeText = _this$config4.closeText, needCloseText = _this$config4.needCloseText;
      var items = [];
      var cIndex = this.curIndex;
      var _curI = this.curIndex;
      if (needCloseText) {
        items.push({
          showText: this.getTextByLang(closeText),
          "data-type": DEFAULT_TYPE.TEXT_CLOSE,
          selected: this.curIndex === -1
        });
        cIndex++;
      }
      list.map(function(item, index) {
        var itemInfo = {
          language: item.language || item.srclang || "",
          "data-id": item.id || ""
        };
        itemInfo.selected = _this2.curIndex === index;
        itemInfo.showText = _this2.getTextByLang(item);
        items.push(itemInfo);
      });
      _get(_getPrototypeOf(TextTrack2.prototype), "renderItemList", this).call(this, items, cIndex);
      this.curIndex = _curI;
      this.curItem = list[_curI];
    }
  }, {
    key: "rePosition",
    value: function rePosition() {
      var fitVideo = this.config.style.fitVideo;
      var _rect = this.player.controls.root.getBoundingClientRect();
      var cHeight = 0 - _rect.height;
      if (!fitVideo) {
        this.subTitles.root.style.transform = "translate(0, ".concat(cHeight, "px)");
        return;
      }
      var _this$player = this.player, video = _this$player.video, root = _this$player.root;
      var _root$getBoundingClie = root.getBoundingClientRect(), height = _root$getBoundingClie.height, width = _root$getBoundingClie.width;
      var videoHeight = video.videoHeight, videoWidth = video.videoWidth;
      var pi = parseInt(videoHeight / videoWidth * 100, 10);
      var vHeight = pi * width / 100;
      if (vHeight > height) {
        vHeight = height;
      }
      var margin = (height - vHeight) / 2 - cHeight;
      if (margin < 0) {
        this.subTitles.root.style.transform = "translate(0, ".concat(margin, "px)");
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      if (this.subTitles) {
        this.subTitles.destroy();
        this.subTitles = null;
      }
      _get(_getPrototypeOf(TextTrack2.prototype), "destroy", this).call(this);
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "texttrack";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return _objectSpread2(_objectSpread2({}, OptionsIcon.defaultConfig), {}, {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 6,
        list: [],
        isDefaultOpen: true,
        style: {
          follow: true,
          mode: "stroke",
          followBottom: 50,
          fitVideo: true,
          offsetBottom: 2,
          baseSizeX: 49,
          baseSizeY: 28,
          minSize: 16,
          minMobileSize: 13,
          line: "double",
          fontColor: "#fff"
        },
        closeText: {
          text: "\u4E0D\u5F00\u542F",
          iconText: "\u5B57\u5E55"
        },
        needCloseText: true,
        className: "xgplayer-texttrack",
        hidePortrait: false,
        isShowIcon: true,
        renderMode: "normal",
        mode: "external",
        debugger: false
      });
    }
  }]);
  return TextTrack2;
}(OptionsIcon);
export { TextTrack as default };
