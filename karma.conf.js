module.exports = function (config) {
    config.set({
        basePath: '.',
        files: [
            'target/web/web-modules/main/webjars/lib/angular/angular.js',
            'target/web/web-modules/main/webjars/lib/angular-mocks/angular-mocks.js',
            'target/web/web-modules/main/webjars/lib/angular-translate/angular-translate.js',
            'target/web/web-modules/main/webjars/lib/angular-resource/angular-resource.js',
            'target/web/web-modules/main/webjars/lib/angular-animate/angular-animate.js',
            'target/web/web-modules/main/webjars/lib/angular-route/angular-route.js',
            'target/web/web-modules/main/webjars/lib/angular-sanitize/angular-sanitize.js',
            'target/web/web-modules/main/webjars/lib/ngstorage/ngStorage.js',
            'target/web/web-modules/main/webjars/lib/jquery/jquery.js',
            'target/web/web-modules/main/webjars/lib/angular-ui-bootstrap/ui-bootstrap-tpls.js',
            'target/web/web-modules/main/webjars/lib/FileSaver.js/FileSaver.js',
            'public/components/vendor/angular-promise-extras.js',
            'public/components/vendor/dialogs.min.js',
            'node_modules/jasmine-jquery/lib/jasmine-jquery.js',
            'public/app/**/*.module.js',
            'public/app/**/*.js',
            'test/unit/**/*Spec.js',
            {pattern: 'test/unit/fixtures/**/*.json', watched: true, served: true, included: false}
        ],
        exclude: [
            'public/components/vendor/MathJax-2.6-latest/**/*.js',
            'public/components/vendor/ckeditor/**/*.js',
            'public/components/vendor/i18n/**/*.js',
            'public/app/exam.js',
            'public/app/exam.min.js*'
        ],
        singleRun: false,
        autoWatch: true,
        frameworks: ['jasmine'],
        browsers: ['Chrome'],
        plugins: [
            'karma-chrome-launcher',
            'karma-phantomjs-launcher',
            'karma-jasmine'
        ],
        reporters: ['dots'],
        logLevel: config.LOG_INFO
    });
};
