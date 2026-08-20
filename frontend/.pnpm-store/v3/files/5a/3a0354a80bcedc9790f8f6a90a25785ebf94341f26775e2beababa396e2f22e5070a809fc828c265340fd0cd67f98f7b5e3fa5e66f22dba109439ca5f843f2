import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import EventEmitter from "eventemitter3";
var NativeSubTitle = /* @__PURE__ */ function(_EventEmitter) {
  _inherits(NativeSubTitle2, _EventEmitter);
  var _super = _createSuper(NativeSubTitle2);
  function NativeSubTitle2(media) {
    var _this;
    _classCallCheck(this, NativeSubTitle2);
    _this = _super.call(this);
    _defineProperty(_assertThisInitialized(_this), "_onChange", function(e) {
      var _list = _this._media.textTracks;
      if (!_list || _list.length === 0) {
        return;
      }
      var retList = [];
      var langs = [];
      var curIndex = -1;
      for (var i = 0; i < _list.length; i++) {
        var item = _list[i];
        if (item.kind === "subtitles") {
          retList.push({
            id: item.id || item.language,
            language: item.language,
            text: item.label,
            isDefault: item.mode === "showing"
          });
          if (curIndex === -1 && item.mode === "showing") {
            curIndex = i;
          }
          langs.push(item.language);
        }
      }
      if (langs.join("|") !== _this._languages) {
        _this._languages = langs.join("|");
        _this.emit("reset", {
          list: retList,
          isOpen: curIndex > -1
        });
      } else if (curIndex === -1) {
        _this.emit("off");
      } else if (_this.curIndex !== curIndex) {
        _this.emit("change", retList[curIndex]);
      }
      _this.curIndex = curIndex;
    });
    _this._media = media;
    _this._list = [];
    _this._languages = "";
    _this.curIndex = -1;
    _this._init();
    return _this;
  }
  _createClass(NativeSubTitle2, [{
    key: "_init",
    value: function _init() {
      var _list = this._media.textTracks;
      _list.addEventListener("change", this._onChange);
    }
  }, {
    key: "switch",
    value: function _switch(data) {
      var _tracks = this._media.textTracks;
      for (var i = 0; i < _tracks.length; i++) {
        var item = _tracks[i];
        if (item.language === data.language) {
          item.mode = "showing";
        } else if (item.mode === "showing") {
          item.mode = "disabled";
        }
      }
    }
  }, {
    key: "switchOff",
    value: function switchOff() {
      var _tracks = this._media.textTracks;
      for (var i = 0; i < _tracks.length; i++) {
        _tracks[i].mode = "disabled";
      }
      this.curIndex = -1;
    }
  }, {
    key: "destroy",
    value: function destroy() {
      var _list = this._media.textTracks;
      _list.removeEventListener("change", this._onChange);
      this._media = null;
      this._list = [];
      this._languages = "";
      this.curIndex = -1;
    }
  }]);
  return NativeSubTitle2;
}(EventEmitter);
export { NativeSubTitle as default };
