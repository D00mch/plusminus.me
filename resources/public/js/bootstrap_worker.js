CLOSURE_BASE_PATH = "out_worker/goog/";
/**
 * Imports a script using the Web Worker importScript API.
 *
 * @param {string} src The script source.
 * @return {boolean} True if the script was imported, false otherwise.
 */
this.CLOSURE_IMPORT_SCRIPT = (function(global) {
    return function(src) {
        global['importScripts'](src);
        return true;
    };
})(this);

if(typeof goog == "undefined") importScripts(CLOSURE_BASE_PATH + "base.js");

importScripts("worker.js");
goog.require('plus_minus.worker');

if('serviceWorker' in navigator) {  // if it's in home.html, I have 'goog not found'
    navigator.serviceWorker.register('js/worker.js')
        .then(function() {
            console.log('Service Worker Registered');
        });
}
