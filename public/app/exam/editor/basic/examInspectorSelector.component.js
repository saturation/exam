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
angular.module('app.exam.editor')
    .component('examInspectorSelector', {
        templateUrl: '/assets/app/exam/editor/basic/examInspectorSelector.template.html',
        bindings: {
            exam: '<'
        },
        controller: ['$translate', 'limitToFilter', 'ExamRes', 'UserRes', 'toast',
            function ($translate, limitToFilter, ExamRes, UserRes, toast) {

                var vm = this;

                vm.$onInit = function () {
                    vm.newInspector = {
                        id: null,
                        name: null,
                        sendMessage: false,
                        comment: ''
                    };
                    getInspectors();
                };

                vm.allInspectors = function (filter, criteria) {

                    return UserRes.filterUsersByExam.query({
                        role: 'TEACHER',
                        eid: vm.exam.id,
                        q: criteria
                    }).$promise.then(
                        function (names) {
                            return limitToFilter(names, 15);
                        },
                        function (error) {
                            toast.error(error.data);
                        });
                };

                vm.setInspector = function ($item, $model, $label) {
                    vm.newInspector.id = $item.id;
                };

                vm.addInspector = function () {
                    if (vm.newInspector.id > 0) {
                        ExamRes.inspection.insert({
                            eid: vm.exam.id,
                            uid: vm.newInspector.id,
                            comment: vm.newInspector.comment || ''
                        }, function () {
                            // reload the list
                            getInspectors();
                            // clear input field
                            delete vm.newInspector;

                        }, function (error) {
                            toast.error(error.data);
                        });
                    } else {
                        toast.error($translate.instant('sitnet_teacher_not_found'));
                    }
                };

                vm.removeInspector = function (id) {
                    ExamRes.inspector.remove({id: id},
                        function () {
                            getInspectors();
                        },
                        function (error) {
                            toast.error(error.data);
                        });
                };

                function getInspectors() {
                    ExamRes.inspections.get({id: vm.exam.id},
                        function (inspections) {
                            vm.examInspections = inspections;
                        },
                        function (error) {
                            toast.error(error.data);
                        });
                }


            }]
    });
