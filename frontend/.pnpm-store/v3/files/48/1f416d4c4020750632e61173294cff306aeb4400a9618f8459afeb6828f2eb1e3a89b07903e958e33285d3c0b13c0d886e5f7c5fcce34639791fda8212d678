export default I18N;
export type IXGI18nText = {
    LANG: string;
    TEXT: {
        [propName: string]: any;
    };
};
export type IXGI18n = {
    lang: {
        [propName: string]: IXGI18nText;
    };
    langKeys: Array<string>;
    textKeys: {
        [propName: string]: string;
    };
};
declare namespace I18N {
    export { extend };
    export { use };
    export { init };
}
/**
 * @param { Array<IXGI18nText> } i18nTextList
 * @param { IXGI18n } [i18nLangs]
 */
declare function extend(i18nTextList: Array<IXGI18nText>, i18nLangs?: IXGI18n): void;
/**
 * @param { IXGI18nText } langData
 * @param { IXGI18n } [i18nLangs]
 */
declare function use(langData: IXGI18nText, i18nLangs?: IXGI18n): void;
/**
 *
 * @returns { IXGI18n }
 */
declare function init(id: any): IXGI18n;
