import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import sniffer from "../../utils/sniffer.js";
import { VOLUME_CHANGE, LOADED_DATA } from "../../events.js";
import "../../utils/debug.js";
import Plugin, { POSITIONS } from "../../plugin/plugin.js";
import volumeLargeSvg from "../assets/volumeLarge.js";
import volumeSmallSvg from "../assets/volumeSmall.js";
import volumeMutedSvg from "../assets/volumeMuted.js";
var Volume = /* @__PURE__ */ function(_Plugin) {
  _inherits(Volume2, _Plugin);
  var _super = _createSuper(Volume2);
  function Volume2() {
    var _this;
    _classCallCheck(this, Volume2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "onBarMousedown", function(e) {
      var _assertThisInitialize = _assertThisInitialized(_this), player = _assertThisInitialize.player;
      var bar = _this.find(".xgplayer-bar");
      util.event(e);
      var barRect = bar.getBoundingClientRect();
      var pos = util.getEventPos(e, player.zoom);
      var height = barRect.height - (pos.clientY - barRect.top);
      pos.h = height;
      pos.barH = barRect.height;
      _this.pos = pos;
      if (height < -2) {
        return;
      }
      _this.updateVolumePos(height, e);
      document.addEventListener("mouseup", _this.onBarMouseUp);
      _this._d.isStart = true;
      return false;
    });
    _defineProperty(_assertThisInitialized(_this), "onBarMouseMove", function(e) {
      var _assertThisInitialize2 = _assertThisInitialized(_this), _d = _assertThisInitialize2._d;
      if (!_d.isStart) {
        return;
      }
      var _assertThisInitialize3 = _assertThisInitialized(_this), pos = _assertThisInitialize3.pos, player = _assertThisInitialize3.player;
      e.preventDefault();
      e.stopPropagation();
      util.event(e);
      var _ePos = util.getEventPos(e, player.zoom);
      _d.isMoving = true;
      var w = pos.h - _ePos.clientY + pos.clientY;
      if (w > pos.barH) {
        return;
      }
      _this.updateVolumePos(w, e);
    });
    _defineProperty(_assertThisInitialized(_this), "onBarMouseUp", function(e) {
      util.event(e);
      document.removeEventListener("mouseup", _this.onBarMouseUp);
      var _assertThisInitialize4 = _assertThisInitialized(_this), _d = _assertThisInitialize4._d;
      _d.isStart = false;
      _d.isMoving = false;
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseenter", function(e) {
      _this._d.isActive = true;
      _this.focus();
      _this.emit("icon_mouseenter", {
        pluginName: _this.pluginName
      });
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseleave", function(e) {
      _this._d.isActive = false;
      _this.unFocus(100, false, e);
      _this.emit("icon_mouseleave", {
        pluginName: _this.pluginName
      });
    });
    _defineProperty(_assertThisInitialized(_this), "onVolumeChange", function(e) {
      if (!_this.player) {
        return;
      }
      var _this$player = _this.player, muted = _this$player.muted, volume = _this$player.volume;
      if (!_this._d.isMoving) {
        _this.find(".xgplayer-drag").style.height = muted || volume === 0 ? "4px" : "".concat(volume * 100, "%");
        if (_this.config.showValueLabel) {
          _this.updateVolumeValue();
        }
      }
      _this.animate(muted, volume);
    });
    return _this;
  }
  _createClass(Volume2, [{
    key: "registerIcons",
    value: function registerIcons() {
      return {
        volumeSmall: {
          icon: volumeSmallSvg,
          class: "xg-volume-small"
        },
        volumeLarge: {
          icon: volumeLargeSvg,
          class: "xg-volume"
        },
        volumeMuted: {
          icon: volumeMutedSvg,
          class: "xg-volume-mute"
        }
      };
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      this._timerId = null;
      this._d = {
        isStart: false,
        isMoving: false,
        isActive: false
      };
      if (this.config.disable) {
        return;
      }
      this.initIcons();
      var _this$playerConfig = this.playerConfig, commonStyle = _this$playerConfig.commonStyle, volume = _this$playerConfig.volume;
      if (commonStyle.volumeColor) {
        this.find(".xgplayer-drag").style.backgroundColor = commonStyle.volumeColor;
      }
      this.changeMutedHandler = this.hook("mutedChange", function(e) {
        _this2.changeMuted(e);
      }, {
        pre: function pre(e) {
          e.preventDefault();
          e.stopPropagation();
        }
      });
      this._onMouseenterHandler = this.hook("mouseenter", this.onMouseenter);
      this._onMouseleaveHandler = this.hook("mouseleave", this.onMouseleave);
      if (!(sniffer.device === "mobile") && this.playerConfig.isMobileSimulateMode !== "mobile") {
        this.bind("mouseenter", this._onMouseenterHandler);
        this.bind(["blur", "mouseleave"], this._onMouseleaveHandler);
        this.bind(".xgplayer-slider", "mousedown", this.onBarMousedown);
        this.bind(".xgplayer-slider", "mousemove", this.onBarMouseMove);
        this.bind(".xgplayer-slider", "mouseup", this.onBarMouseUp);
      }
      this.bind(".xgplayer-icon", ["touchend", "click"], this.changeMutedHandler);
      this.on(VOLUME_CHANGE, this.onVolumeChange);
      this.once(LOADED_DATA, this.onVolumeChange);
      if (util.typeOf(volume) !== "Number") {
        this.player.volume = this.config.default;
      }
      this.onVolumeChange();
    }
  }, {
    key: "updateVolumePos",
    value: function updateVolumePos(height, event) {
      var player = this.player;
      var drag = this.find(".xgplayer-drag");
      var bar = this.find(".xgplayer-bar");
      if (!bar || !drag) {
        return;
      }
      var now = parseInt(height / bar.getBoundingClientRect().height * 1e3, 10);
      drag.style.height = "".concat(height, "px");
      var to = Math.max(Math.min(now / 1e3, 1), 0);
      var props = {
        volume: {
          from: player.volume,
          to
        }
      };
      if (player.muted) {
        props.muted = {
          from: true,
          to: false
        };
      }
      this.emitUserAction(event, "change_volume", {
        muted: player.muted,
        volume: player.volume,
        props
      });
      player.volume = Math.max(Math.min(now / 1e3, 1), 0);
      player.muted && (player.muted = false);
      if (this.config.showValueLabel) {
        this.updateVolumeValue();
      }
    }
  }, {
    key: "updateVolumeValue",
    value: function updateVolumeValue() {
      var _this$player2 = this.player, volume = _this$player2.volume, muted = _this$player2.muted;
      var $labelValue = this.find(".xgplayer-value-label");
      var vol = Math.max(Math.min(volume, 1), 0);
      $labelValue.innerText = muted ? 0 : Math.round(vol * 100);
    }
  }, {
    key: "focus",
    value: function focus() {
      var player = this.player;
      player.focus({
        autoHide: false
      });
      if (this._timerId) {
        util.clearTimeout(this, this._timerId);
        this._timerId = null;
      }
      util.addClass(this.root, "slide-show");
    }
  }, {
    key: "unFocus",
    value: function unFocus() {
      var _this3 = this;
      var delay = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : 100;
      var isForce = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : true;
      var e = arguments.length > 2 ? arguments[2] : void 0;
      var _d = this._d, player = this.player;
      if (_d.isActive) {
        return;
      }
      if (this._timerId) {
        util.clearTimeout(this, this._timerId);
        this._timerId = null;
      }
      this._timerId = util.setTimeout(this, function() {
        if (!_d.isActive) {
          isForce ? player.blur() : player.focus();
          util.removeClass(_this3.root, "slide-show");
          _d.isStart && _this3.onBarMouseUp(e);
        }
        _this3._timerId = null;
      }, delay);
    }
  }, {
    key: "changeMuted",
    value: function changeMuted(e) {
      e && e.stopPropagation();
      var player = this.player, _d = this._d;
      _d.isStart && this.onBarMouseUp(e);
      this.emitUserAction(e, "change_muted", {
        muted: player.muted,
        volume: player.volume,
        props: {
          muted: {
            from: player.muted,
            to: !player.muted
          }
        }
      });
      if (player.volume > 0) {
        player.muted = !player.muted;
      }
      if (player.volume < 0.01) {
        player.volume = this.config.miniVolume;
      }
    }
  }, {
    key: "animate",
    value: function animate(muted, volume) {
      if (muted || volume === 0) {
        this.setAttr("data-state", "mute");
      } else if (volume < 0.5 && this.icons.volumeSmall) {
        this.setAttr("data-state", "small");
      } else {
        this.setAttr("data-state", "normal");
      }
    }
  }, {
    key: "initIcons",
    value: function initIcons() {
      var icons = this.icons;
      this.appendChild(".xgplayer-icon", icons.volumeSmall);
      this.appendChild(".xgplayer-icon", icons.volumeLarge);
      this.appendChild(".xgplayer-icon", icons.volumeMuted);
    }
  }, {
    key: "destroy",
    value: function destroy() {
      if (this._timerId) {
        util.clearTimeout(this, this._timerId);
        this._timerId = null;
      }
      this.unbind("mouseenter", this.onMouseenter);
      this.unbind(["blur", "mouseleave"], this.onMouseleave);
      this.unbind(".xgplayer-slider", "mousedown", this.onBarMousedown);
      this.unbind(".xgplayer-slider", "mousemove", this.onBarMouseMove);
      this.unbind(".xgplayer-slider", "mouseup", this.onBarMouseUp);
      document.removeEventListener("mouseup", this.onBarMouseUp);
      this.unbind(".xgplayer-icon", sniffer.device === "mobile" ? "touchend" : "click", this.changeMutedHandler);
    }
  }, {
    key: "render",
    value: function render() {
      if (this.config.disable) {
        return;
      }
      var volume = this.config.default || this.player.volume;
      var isShowVolumeValue = this.config.showValueLabel;
      return '\n    <xg-icon class="xgplayer-volume" data-state="normal">\n      <div class="xgplayer-icon">\n      </div>\n      <xg-slider class="xgplayer-slider">\n        '.concat(isShowVolumeValue ? '<div class="xgplayer-value-label">'.concat(volume * 100, "</div>") : "", '\n        <div class="xgplayer-bar">\n          <xg-drag class="xgplayer-drag" style="height: ').concat(volume * 100, '%"></xg-drag>\n        </div>\n      </xg-slider>\n    </xg-icon>');
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "volume";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 1,
        disable: false,
        showValueLabel: false,
        default: 0.6,
        miniVolume: 0.2
      };
    }
  }]);
  return Volume2;
}(Plugin);
export { Volume as default };
