import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass, get as _get, getPrototypeOf as _getPrototypeOf } from "../../_virtual/_rollupPluginBabelHelpers.js";
import downloadUtil from "downloadjs";
import util from "../../utils/util.js";
import "../../utils/debug.js";
import { POSITIONS } from "../../plugin/plugin.js";
import { xgIconTips } from "../common/iconTools.js";
import Icon from "../common/iconPlugin.js";
import DownloadSvg from "../assets/download.js";
var Download = /* @__PURE__ */ function(_IconPlugin) {
  _inherits(Download2, _IconPlugin);
  var _super = _createSuper(Download2);
  function Download2(args) {
    var _this;
    _classCallCheck(this, Download2);
    _this = _super.call(this, args);
    _defineProperty(_assertThisInitialized(_this), "download", function(e) {
      if (_this.isLock) {
        return;
      }
      _this.emitUserAction(e, "download");
      var url = _this.playerConfig.url;
      var dUrl = "";
      if (util.typeOf(url) === "String") {
        dUrl = url;
      } else if (util.typeOf(url) === "Array" && url.length > 0) {
        dUrl = url[0].src;
      }
      var newUrl = _this.getAbsoluteURL(dUrl);
      downloadUtil(newUrl);
      _this.isLock = true;
      _this.timer = window.setTimeout(function() {
        _this.isLock = false;
        window.clearTimeout(_this.timer);
        _this.timer = null;
      }, 300);
    });
    _this.timer = null;
    _this.isLock = false;
    return _this;
  }
  _createClass(Download2, [{
    key: "afterCreate",
    value: function afterCreate() {
      _get(_getPrototypeOf(Download2.prototype), "afterCreate", this).call(this);
      if (this.config.disable) {
        return;
      }
      this.appendChild(".xgplayer-icon", this.icons.download);
      this._handler = this.hook("click", this.download, {
        pre: function pre(e) {
          e.preventDefault();
          e.stopPropagation();
        }
      });
      this.bind(["click", "touchend"], this._handler);
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        download: DownloadSvg
      };
    }
  }, {
    key: "getAbsoluteURL",
    value: function getAbsoluteURL(url) {
      if (!url.match(/^https?:\/\//)) {
        var div = document.createElement("div");
        div.innerHTML = '<a href="'.concat(url, '">x</a>');
        url = div.firstChild.href;
      }
      return url;
    }
  }, {
    key: "destroy",
    value: function destroy() {
      _get(_getPrototypeOf(Download2.prototype), "destroy", this).call(this);
      this.unbind(["click", "touchend"], this.download);
      window.clearTimeout(this.timer);
      this.timer = null;
    }
  }, {
    key: "render",
    value: function render() {
      if (this.config.disable) {
        return;
      }
      return '<xg-icon class="xgplayer-download">\n   <div class="xgplayer-icon">\n   </div>\n   '.concat(xgIconTips(this, "DOWNLOAD_TIPS", this.playerConfig.isHideTips), "\n    </xg-icon>");
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "download";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 3,
        disable: true
      };
    }
  }]);
  return Download2;
}(Icon);
export { Download as default };
