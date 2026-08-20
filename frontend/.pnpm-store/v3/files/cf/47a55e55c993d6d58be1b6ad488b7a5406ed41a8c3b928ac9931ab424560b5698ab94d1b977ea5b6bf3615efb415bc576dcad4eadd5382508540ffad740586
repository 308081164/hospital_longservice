import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass, get as _get, getPrototypeOf as _getPrototypeOf } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { VIDEO_RESIZE, ROTATE } from "../../events.js";
import "../../utils/debug.js";
import { POSITIONS } from "../../plugin/plugin.js";
import { xgIconTips } from "../common/iconTools.js";
import Icon from "../common/iconPlugin.js";
import RotateSvg from "../assets/rotate.js";
var Rotate = /* @__PURE__ */ function(_IconPlugin) {
  _inherits(Rotate2, _IconPlugin);
  var _super = _createSuper(Rotate2);
  function Rotate2(args) {
    var _this;
    _classCallCheck(this, Rotate2);
    _this = _super.call(this, args);
    _this.rotateDeg = _this.config.rotateDeg || 0;
    return _this;
  }
  _createClass(Rotate2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      if (this.config.disable) {
        return;
      }
      _get(_getPrototypeOf(Rotate2.prototype), "afterCreate", this).call(this);
      this.appendChild(".xgplayer-icon", this.icons.rotate);
      this.onBtnClick = this.onBtnClick.bind(this);
      this.bind(".xgplayer-icon", ["click", "touchend"], this.onBtnClick);
      this.on(VIDEO_RESIZE, function() {
        if (_this2.rotateDeg && _this2.config.innerRotate) {
          util.setTimeout(_this2, function() {
            _this2.updateRotateDeg(_this2.rotateDeg, _this2.config.innerRotate);
          }, 100);
        }
      });
      var root = this.player.root;
      this.rootWidth = root.style.width || root.offsetWidth || root.clientWidth;
      this.rootHeight = root.style.height || root.offsetHeight || root.clientHeight;
      if (this.rotateDeg) {
        this.updateRotateDeg(this.rotateDeg, this.config.innerRotate);
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      _get(_getPrototypeOf(Rotate2.prototype), "destroy", this).call(this);
      this.unbind(".xgplayer-icon", ["click", "touchend"], this.onBtnClick);
    }
  }, {
    key: "onBtnClick",
    value: function onBtnClick(e) {
      e.preventDefault();
      e.stopPropagation();
      this.emitUserAction(e, "rotate");
      this.rotate(this.config.clockwise, this.config.innerRotate, 1);
    }
  }, {
    key: "updateRotateDeg",
    value: function updateRotateDeg(rotateDeg, innerRotate) {
      if (!rotateDeg) {
        rotateDeg = 0;
      }
      if (innerRotate) {
        this.player.videoRotateDeg = rotateDeg;
        return;
      }
      var player = this.player, rootWidth = this.rootWidth, rootHeight = this.rootHeight;
      var root = player.root, innerContainer = player.innerContainer;
      var video = player.media;
      var width = root.offsetWidth;
      var height = innerContainer && innerRotate ? innerContainer.offsetHeight : root.offsetHeight;
      var rWidth = rootWidth;
      var rHeight = rootHeight;
      var x = 0;
      var y = 0;
      if (rotateDeg === 0.75 || rotateDeg === 0.25) {
        rWidth = "".concat(height, "px");
        rHeight = "".concat(width, "px");
        x = -(height - width) / 2;
        y = -(width - height) / 2;
      }
      var _transform = "translate(".concat(x, "px,").concat(y, "px) rotate(").concat(rotateDeg, "turn)");
      var _styles = {
        transformOrigin: "center center",
        transform: _transform,
        webKitTransform: _transform,
        height: rHeight,
        width: rWidth
      };
      var _target = innerRotate ? video : root;
      var poster = innerRotate ? player.getPlugin("poster") : null;
      Object.keys(_styles).map(function(key) {
        _target.style[key] = _styles[key];
        poster && poster.root && (poster.root.style[key] = _styles[key]);
      });
    }
  }, {
    key: "rotate",
    value: function rotate() {
      var clockwise = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : false;
      var innerRotate = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : true;
      var times = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : 1;
      var player = this.player;
      if (!this.rotateDeg) {
        this.rotateDeg = 0;
      }
      var factor = clockwise ? 1 : -1;
      this.rotateDeg = (this.rotateDeg + 1 + factor * 0.25 * times) % 1;
      this.updateRotateDeg(this.rotateDeg, innerRotate);
      player.emit(ROTATE, this.rotateDeg * 360);
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        rotate: RotateSvg
      };
    }
  }, {
    key: "render",
    value: function render() {
      if (this.config.disable) {
        return;
      }
      return '\n    <xg-icon class="xgplayer-rotate">\n      <div class="xgplayer-icon">\n      </div>\n      '.concat(xgIconTips(this, "ROTATE_TIPS", this.playerConfig.isHideTips), "\n    </xg-icon>");
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "rotate";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 6,
        innerRotate: true,
        clockwise: false,
        rotateDeg: 0,
        disable: false
      };
    }
  }]);
  return Rotate2;
}(Icon);
export { Rotate as default };
