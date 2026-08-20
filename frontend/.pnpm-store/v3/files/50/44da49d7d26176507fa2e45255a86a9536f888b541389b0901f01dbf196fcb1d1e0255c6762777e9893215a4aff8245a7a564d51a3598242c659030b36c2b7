import { createClass as _createClass, classCallCheck as _classCallCheck } from "./_virtual/_rollupPluginBabelHelpers.js";
import version from "./version.js";
import util from "./utils/util.js";
var ERROR_TYPE_MAP = {
  1: "media",
  2: "media",
  3: "media",
  4: "media",
  5: "media",
  6: "media"
};
var ERROR_MAP = {
  1: 5101,
  2: 5102,
  3: 5103,
  4: 5104,
  5: 5105,
  6: 5106
};
var Errors = /* @__PURE__ */ _createClass(
  function Errors2(player) {
    var errorInfo = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {
      errorType: "",
      errorCode: 0,
      errorMessage: "",
      originError: "",
      ext: {},
      mediaError: null
    };
    _classCallCheck(this, Errors2);
    var ERROR_TYPES = player && player.i18n ? player.i18n.ERROR_TYPES : null;
    if (player.media) {
      var mediaError = errorInfo.mediaError ? errorInfo.mediaError : player.media.error || {};
      var duration = player.duration, currentTime = player.currentTime, ended = player.ended, src = player.src, currentSrc = player.currentSrc;
      var _player$media = player.media, readyState = _player$media.readyState, networkState = _player$media.networkState;
      var _errc = errorInfo.errorCode || mediaError.code;
      if (ERROR_MAP[_errc]) {
        _errc = ERROR_MAP[_errc];
      }
      var r = {
        playerVersion: version,
        currentTime,
        duration,
        ended,
        readyState,
        networkState,
        src: src || currentSrc,
        errorType: errorInfo.errorType,
        errorCode: _errc,
        message: errorInfo.errorMessage || mediaError.message,
        mediaError,
        originError: errorInfo.originError ? errorInfo.originError.stack : "",
        host: util.getHostFromUrl(src || currentSrc)
      };
      errorInfo.ext && Object.keys(errorInfo.ext).map(function(key) {
        r[key] = errorInfo.ext[key];
      });
      return r;
    } else {
      if (arguments.length > 1) {
        var _r = {
          playerVersion: version,
          domain: document.domain
        };
        var arr = ["errorType", "currentTime", "duration", "networkState", "readyState", "src", "currentSrc", "ended", "errd", "errorCode", "mediaError"];
        for (var i = 0; i < arguments.length; i++) {
          _r[arr[i]] = arguments[i];
        }
        _r.ex = ERROR_TYPES ? (ERROR_TYPES[arguments[0]] || {}).msg : "";
        return _r;
      }
    }
  }
);
export { ERROR_TYPE_MAP, Errors as default };
