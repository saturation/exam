/*
 * Copyright (c) 2017 The members of the EXAM Consortium (https://confluence.csc.fi/display/EXAM/Konsortio-organisaatio)
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed
 * on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

'use strict';
angular.module('app.utility')
    .factory('Files', ['$q', '$http', '$translate', '$timeout', 'SettingsResource', 'toast',
        function ($q, $http, $translate, $timeout, SettingsResource, toast) {
            var _supportsBlobUrls;
            var _maxFileSize;

            var svg = new Blob(
                ['<svg xmlns=\'http://www.w3.org/2000/svg\'></svg>'],
                {type: 'image/svg+xml;charset=utf-8'}
            );
            var img = new Image();
            img.onload = function () {
                _supportsBlobUrls = true;
            };
            img.onerror = function () {
                _supportsBlobUrls = false;
            };
            img.src = URL.createObjectURL(svg);

            var saveFile = function (data, fileName, contentType) {
                if (!_supportsBlobUrls) {
                    window.open('data:' + contentType + ';base64,' + data);
                } else {
                    var byteString = atob(data);
                    var ab = new ArrayBuffer(byteString.length);
                    var ia = new Uint8Array(ab);
                    for (var i = 0; i < byteString.length; i++) {
                        ia[i] = byteString.charCodeAt(i);
                    }
                    var blob = new Blob([ia], {type: contentType});
                    saveAs(blob, fileName);
                }
            };

            var download = function (url, filename, params, post) {
                var res = post ? $http.post : $http.get;
                res(url, {params: params}).success(function (data, status, headers) {
                    var contentType = headers()['content-type'].split(';')[0];
                    saveFile(data, filename, contentType);
                }).error(function (error) {
                    toast.error(error.data || error);
                });
            };

            var downloadUrl = function (url, filename, params) {
                var deferred = $q.defer();
                $http.get(url, {params: params}).success(function (data, status, headers) {
                    var contentType = headers()['content-type'].split(';')[0];
                    return deferred.resolve({url: 'data:' + contentType + ';base64, ' + data});
                }).error(function (error) {
                    toast.error(error.data || error);
                    return deferred.reject();
                });
                return deferred.promise;
            };

            var open = function (file, filename) {
                var reader = new FileReader();
                reader.onload = function (e) {
                    var f = reader.result;
                    window.open(f);
                };
                reader.readAsDataURL(file);
            };

            var getMaxFilesize = function () {
                var deferred = $q.defer();

                if (_maxFileSize) {
                    $timeout(function () {
                        return deferred.resolve({'filesize': _maxFileSize});
                    }, 10);
                }
                SettingsResource.maxFilesize.get(function (data) {
                    _maxFileSize = data.filesize;
                    return deferred.resolve(data);
                }, function (error) {
                    return deferred.reject(error);
                });
                return deferred.promise;
            };

            var isFileTooBig = function (file) {
                if (file.size > _maxFileSize) {
                    toast.error($translate.instant('sitnet_file_too_large'));
                    return true;
                }
                return false;
            };

            var doUpload = function (url, file, params, parent, modal, callback) {
                if (isFileTooBig(file)) {
                    return;
                }
                var fd = new FormData();
                fd.append('file', file);
                for (var k in params) {
                    if (params.hasOwnProperty(k)) {
                        fd.append(k, params[k]);
                    }
                }

                $http.post(url, fd, {
                    transformRequest: angular.identity,
                    headers: {'Content-Type': undefined}
                })
                    .success(callback)
                    .error(function (error) {
                        if (modal) {
                            modal.dismiss();
                        }
                        toast.error(error);
                    });
            };

            var upload = function (url, file, params, parent, modal, callback) {
                doUpload(url, file, params, parent, modal, function (attachment) {
                    if (modal) {
                        modal.dismiss();
                    }
                    if (parent) {
                        parent.attachment = attachment;
                    }
                    if (callback) {
                        callback();
                    }
                });
            };

            var uploadAnswerAttachment = function (url, file, params, parent, modal) {
                doUpload(url, file, params, parent, modal, function (answer) {
                    if (modal) {
                        modal.dismiss();
                    }
                    parent.objectVersion = answer.objectVersion;
                    parent.attachment = answer.attachment;
                });
            };

            return {
                download: download,
                downloadUrl: downloadUrl,
                upload: upload,
                uploadAnswerAttachment: uploadAnswerAttachment,
                getMaxFilesize: getMaxFilesize,
                isFileTooBig: isFileTooBig,
                open: open
            };
        }]);


