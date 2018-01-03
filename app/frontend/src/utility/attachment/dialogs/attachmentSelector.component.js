/*
 * Copyright (c) 2017 Exam Consortium
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

angular.module('app.utility')
    .component('attachmentSelector', {
        templateUrl: '/assets/app/utility/attachment/dialogs/attachmentSelector.template.html',
        bindings: {
            close: '&',
            dismiss: '&',
            resolve: '<'
        },
        controller: ['$scope', 'Files', function ($scope, Files) {

            const vm = this;

            vm.$onInit = function () {
                vm.title = vm.resolve.title || 'sitnet_attachment_selection';
                vm.isTeacherModal = vm.resolve.isTeacherModal;
                Files.getMaxFilesize().then(function (data) {
                    vm.maxFileSize = data.filesize;
                });
            };

            vm.ok = function () {
                vm.close({
                    $value: {'attachmentFile': vm.attachmentFile}
                });
            };

            vm.cancel = function () {
                vm.dismiss({$value: 'cancel'});
            };

            // Close modal if user clicked the back button and no changes made
            $scope.$on('$routeChangeStart', function () {
                if (!window.onbeforeunload) {
                    vm.cancel();
                }
            });

        }]
    });
