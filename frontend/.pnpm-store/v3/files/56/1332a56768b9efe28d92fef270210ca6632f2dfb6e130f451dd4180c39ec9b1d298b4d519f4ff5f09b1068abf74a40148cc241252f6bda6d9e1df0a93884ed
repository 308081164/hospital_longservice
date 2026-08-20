import { createUnplugin } from "unplugin";
import { createFilter } from "unplugin-utils";
import { init, parse } from "es-module-lexer";
import MagicString from "magic-string";

//#region node_modules/.pnpm/escape-string-regexp@5.0.0/node_modules/escape-string-regexp/index.js
function escapeStringRegexp(string) {
	if (typeof string !== "string") throw new TypeError("Expected a string");
	return string.replace(/[|\\{}()[\]^$+*?.]/g, "\\$&").replace(/-/g, "\\x2d");
}

//#endregion
//#region src/core/default-locale.ts
function getLocaleRE(options) {
	return new RegExp(`${escapeStringRegexp(`${options.lib}/`)}(es|lib)${escapeStringRegexp("/hooks/use-locale/index")}`);
}
function transformDefaultLocale(options, source, id) {
	if (!getLocaleRE(options).test(id)) return;
	return source.replace("locale/lang/en", `locale/lang/${options.defaultLocale}`);
}
function getViteDepPlugin(options) {
	const localeImporterRE = new RegExp(`${escapeStringRegexp(`node_modules/${options.lib}/`)}(es|lib)${escapeStringRegexp("/hooks/use-locale/index")}`);
	const localePath = "/locale/lang/en";
	const localePathFixed = `/locale/lang/${options.defaultLocale}`;
	return {
		name: "unplugin-element-plus:default-locale",
		setup(build) {
			build.onResolve({
				filter: new RegExp(escapeStringRegexp(localePath)),
				namespace: "file"
			}, ({ path, importer, kind, resolveDir }) => {
				if (localeImporterRE.test(importer)) return build.resolve(path.replace(localePath, localePathFixed), {
					importer,
					kind,
					resolveDir
				});
			});
		}
	};
}

//#endregion
//#region src/core/style.ts
const hyphenateRE = /\B([A-Z])/g;
const hyphenate = (str) => str.replaceAll(hyphenateRE, "-$1").toLowerCase();
const formatMap = {
	cjs: "lib",
	esm: "es"
};
const multilineCommentsRE = /\/\*\s(.|[\n\r])*?\*\//g;
const singlelineCommentsRE = /\/\/\s.*/g;
function stripeComments(code) {
	return code.replaceAll(multilineCommentsRE, "").replaceAll(singlelineCommentsRE, "");
}
function transformImportStyle(specifier, source, useSource = false, options) {
	const { prefix, lib, format, ignoreComponents } = options;
	const statement = stripeComments(source.slice(specifier.ss, specifier.se));
	const leftBracket = statement.indexOf("{");
	if (leftBracket !== -1) {
		const identifiers = statement.slice(leftBracket + 1, statement.indexOf("}"));
		const components = identifiers.split(",");
		const styleImports = [];
		components.forEach((c) => {
			const trimmed = c.replace(/\sas\s.+/, "").trim();
			if (trimmed.startsWith(prefix)) {
				const component = trimmed.slice(prefix.length);
				if (ignoreComponents.includes(component)) return;
				if (useSource) styleImports.push(`import '${lib}/${formatMap[format]}/components/${hyphenate(component)}/style/index'`);
				else styleImports.push(`import '${lib}/${formatMap[format]}/components/${hyphenate(component)}/style/css'`);
			}
		});
		return styleImports.join("\n");
	}
}
async function transformStyle(source, options) {
	const { useSource, lib, prefix, format, ignoreComponents } = options;
	if (!source) return;
	await init;
	const specifiers = parse(source)[0].filter(({ n }) => {
		return n === lib || n === `${lib}/es/components` || n === `${lib}/lib/components`;
	});
	if (specifiers.length === 0) return;
	const styleImports = specifiers.map((s$1) => {
		const ret = transformImportStyle(s$1, source, useSource, {
			lib,
			prefix,
			format,
			ignoreComponents
		});
		return ret;
	}).filter((s$1) => s$1).join("\n");
	const lastSpecifier = specifiers.at(-1);
	const s = new MagicString(source);
	s.appendLeft(lastSpecifier.se + 1, `\n${styleImports}\n`);
	return {
		code: s.toString(),
		get map() {
			return s.generateMap({
				hires: true,
				includeContent: true
			});
		}
	};
}

//#endregion
//#region src/index.ts
const defaultOptions = {
	include: [
		"**/*.vue",
		"**/*.ts",
		"**/*.js",
		"**/*.tsx",
		"**/*.jsx",
		"**/*.vue?vue&type=script*"
	],
	exclude: [
		/[/\\]node_modules[/\\]/,
		/[/\\]\.git[/\\]/,
		/[/\\]\.nuxt[/\\]/
	],
	lib: "element-plus",
	ignoreComponents: [],
	useSource: false,
	defaultLocale: "",
	format: "esm",
	prefix: "El",
	sourceMap: false
};
const unplugin = createUnplugin((userOptions = {}) => {
	const options = {
		...defaultOptions,
		...userOptions
	};
	const filter = createFilter(options.include, options.exclude);
	return {
		name: "unplugin-element-plus",
		enforce: "post",
		transformInclude(id) {
			return getLocaleRE(options).test(id) || filter(id);
		},
		transform(source, id) {
			if (options.defaultLocale) {
				const result = transformDefaultLocale(options, source, id);
				if (result) return result;
			}
			return transformStyle(source, options);
		},
		vite: { config() {
			if (options.defaultLocale) return { optimizeDeps: { esbuildOptions: { plugins: [getViteDepPlugin(options)] } } };
		} }
	};
});
var src_default = unplugin;

//#endregion
export { src_default };