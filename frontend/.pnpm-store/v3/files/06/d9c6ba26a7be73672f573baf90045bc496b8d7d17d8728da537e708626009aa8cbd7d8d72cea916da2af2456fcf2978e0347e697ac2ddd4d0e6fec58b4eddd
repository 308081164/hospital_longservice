import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass, objectSpread2 as _objectSpread2, get as _get, getPrototypeOf as _getPrototypeOf } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import { RATE_CHANGE } from "../../events.js";
import "../../utils/debug.js";
import { POSITIONS } from "../../plugin/plugin.js";
import OptionsIcon from "../common/optionsIcon.js";
var PlaybackRate = /* @__PURE__ */ function(_OptionsIcon) {
  _inherits(PlaybackRate2, _OptionsIcon);
  var _super = _createSuper(PlaybackRate2);
  function PlaybackRate2(args) {
    var _this;
    _classCallCheck(this, PlaybackRate2);
    _this = _super.call(this, args);
    _this.curRate = 1;
    return _this;
  }
  _createClass(PlaybackRate2, [{
    key: "beforeCreate",
    value: function beforeCreate(args) {
      var playbackRate = args.player.config.playbackRate;
      var list = !playbackRate ? [] : Array.isArray(playbackRate) ? playbackRate : args.config.list;
      if (Array.isArray(list)) {
        args.config.list = list.map(function(item) {
          if (typeof item === "number") {
            item = {
              rate: item,
              text: "".concat(item, "x")
            };
          } else if (!item.text && item.rate) {
            item.text = "".concat(item.rate, "x");
          }
          return item;
        });
      }
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      _get(_getPrototypeOf(PlaybackRate2.prototype), "afterCreate", this).call(this);
      this.on(RATE_CHANGE, function() {
        if (_this2.curValue === _this2.player.playbackRate) {
          return;
        }
        _this2.renderItemList();
      });
      this.renderItemList();
    }
  }, {
    key: "show",
    value: function show(value) {
      if (!this.config.list || this.config.list.length === 0) {
        return;
      }
      _get(_getPrototypeOf(PlaybackRate2.prototype), "show", this).call(this);
    }
  }, {
    key: "onItemClick",
    value: function onItemClick(e, _data) {
      _get(_getPrototypeOf(PlaybackRate2.prototype), "onItemClick", this).call(this, e, _data);
      var target = e.delegateTarget;
      var rate = Number(target.getAttribute("rate"));
      if (!rate || rate === this.curValue) {
        return false;
      }
      var props = {
        playbackRate: {
          from: this.player.playbackRate,
          to: rate
        }
      };
      this.emitUserAction(e, "change_rate", {
        props
      });
      this.curValue = rate;
      this.player.playbackRate = rate;
    }
  }, {
    key: "renderItemList",
    value: function renderItemList() {
      var _this3 = this;
      var playbackRate = this.player.playbackRate || 1;
      this.curValue = playbackRate;
      var curIndex = -1;
      var items = this.config.list.map(function(item, index) {
        var itemInfo = {
          rate: item.rate
        };
        if (itemInfo.rate === playbackRate) {
          itemInfo.selected = true;
          curIndex = index;
        }
        itemInfo.showText = _this3.getTextByLang(item);
        return itemInfo;
      });
      _get(_getPrototypeOf(PlaybackRate2.prototype), "renderItemList", this).call(this, items, curIndex);
    }
  }, {
    key: "changeCurrentText",
    value: function changeCurrentText() {
      if (this.isIcons) {
        return;
      }
      var list = this.config.list;
      var index = this.curIndex < list.length ? this.curIndex : 0;
      var curItem = list[index];
      var _text = "";
      if (!curItem || this.curIndex < 0) {
        _text = "".concat(this.player.playbackRate, "x");
      } else {
        _text = this.getTextByLang(curItem, "iconText");
      }
      this.find(".icon-text").innerHTML = _text;
    }
  }, {
    key: "destroy",
    value: function destroy() {
      _get(_getPrototypeOf(PlaybackRate2.prototype), "destroy", this).call(this);
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "playbackRate";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return _objectSpread2(_objectSpread2({}, OptionsIcon.defaultConfig), {}, {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 4,
        list: [2, 1.5, 1, 0.75, 0.5],
        className: "xgplayer-playbackrate",
        isShowIcon: true,
        hidePortrait: false
      });
    }
  }]);
  return PlaybackRate2;
}(OptionsIcon);
export { PlaybackRate as default };
