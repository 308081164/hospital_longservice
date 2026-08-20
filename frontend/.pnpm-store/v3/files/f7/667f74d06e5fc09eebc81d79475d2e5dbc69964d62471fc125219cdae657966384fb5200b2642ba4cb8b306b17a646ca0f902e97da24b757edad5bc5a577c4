import { toConsumableArray as _toConsumableArray } from "../_virtual/_rollupPluginBabelHelpers.js";
var usePreset = function usePreset2(player, Preset) {
  var _player$config$plugin, _player$config$ignore;
  var presetInst;
  if (Preset.preset && Preset.options) {
    presetInst = new Preset.preset(Preset.options, player.config);
  } else {
    presetInst = new Preset({}, player.config);
  }
  var _presetInst = presetInst, _presetInst$plugins = _presetInst.plugins, plugins = _presetInst$plugins === void 0 ? [] : _presetInst$plugins, _presetInst$ignores = _presetInst.ignores, ignores = _presetInst$ignores === void 0 ? [] : _presetInst$ignores, _presetInst$icons = _presetInst.icons, icons = _presetInst$icons === void 0 ? {} : _presetInst$icons, _presetInst$i18n = _presetInst.i18n, i18n = _presetInst$i18n === void 0 ? [] : _presetInst$i18n;
  if (!player.config.plugins) {
    player.config.plugins = [];
  }
  if (!player.config.ignores) {
    player.config.ignores = [];
  }
  (_player$config$plugin = player.config.plugins).push.apply(_player$config$plugin, _toConsumableArray(plugins));
  (_player$config$ignore = player.config.ignores).push.apply(_player$config$ignore, _toConsumableArray(ignores));
  Object.keys(icons).map(function(key) {
    if (!player.config.icons[key]) {
      player.config.icons[key] = icons[key];
    }
  });
  var _ci18n = player.config.i18n || [];
  i18n.push.apply(i18n, _toConsumableArray(_ci18n));
  player.config.i18n = i18n;
};
export { usePreset };
