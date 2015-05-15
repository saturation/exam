(function () {
    'use strict';
    angular.module('exam.services')
        .factory('fileService', ['$http', function ($http) {

                var saveFile = function(data, filename) {
                    var byteString = atob(data);
                    var ab = new ArrayBuffer(byteString.length);
                    var ia = new Uint8Array(ab);
                    for (var i = 0; i < byteString.length; i++) {
                        ia[i] = byteString.charCodeAt(i);
                    }
                    var blob = new Blob([ia], {type: "application/octet-stream"});
                    saveAs(blob, filename);
                };

                var download = function(url, filename, params) {
                    $http.get(url, {params: params}).
                        success(function (data) {
                            saveFile(data, filename);
                        }).
                        error(function (error) {
                            toastr.error(error.data || error);
                        });
                };

            return { download: download };
        }]);
}());

