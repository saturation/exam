(function () {
    'use strict';
    angular.module('sitnet')
        .config(['$routeProvider', 'SITNET_CONF', function ($routeProvider, SITNET_CONF) {

            var tmpl = SITNET_CONF.TEMPLATES_PATH;


            /* main navigation */
            $routeProvider.when('/home', { templateUrl: tmpl + 'home.html', controller: 'DashboardCtrl'});
            $routeProvider.when('/questions', { templateUrl: tmpl + 'questions.html'});
            $routeProvider.when('/questions/new', { templateUrl: tmpl + 'question.html'});
            $routeProvider.when('/reports', { templateUrl: tmpl + 'reports.html'});
            $routeProvider.when('/exams', { templateUrl: tmpl + 'exams.html', controller: 'ExamCtrl'});
            $routeProvider.when('/exams/:examId', { templateUrl: tmpl + 'exam.html', controller: 'ExamCtrl'});
            $routeProvider.when('/calendar', { templateUrl: tmpl + 'calendar.html'});
            $routeProvider.when('/notifications', { templateUrl: tmpl + 'notifications.html'});
            $routeProvider.when('/messages', { templateUrl: tmpl + 'messages.html'});
            $routeProvider.when('/tools', { templateUrl: tmpl + 'tools.html'});
            $routeProvider.when('/math', { templateUrl: tmpl + 'math.html'});

            /* extra */
            $routeProvider.when('/user', { templateUrl: tmpl + 'user.html', controller: 'UserCtrl'});
            $routeProvider.when('/users', { templateUrl: tmpl + 'users.html', controller: 'UserCtrl'});
            $routeProvider.when('/about', { templateUrl: tmpl + 'about.html', controller: 'TestCtrl' });
            $routeProvider.when('/login', { templateUrl: tmpl + 'login.html', controller: 'SessionCtrl' });
            $routeProvider.when('/logout', { templateUrl: tmpl + 'logout.html', controller: 'SessionCtrl' });
            $routeProvider.when('/courses', { templateUrl: tmpl + 'courses.html', controller: 'CourseCtrl'});
            $routeProvider.when('/questions', { templateUrl: tmpl + 'questions.html', controller: 'QuestionCtrl'});

            /* Student */
            $routeProvider.when('/student/exams', { templateUrl: tmpl + 'active.html', controller: 'StudentExamController'});
            $routeProvider.when('/student/startexam', { templateUrl: tmpl + 'startexam.html', controller: 'StudentExamController'});
            $routeProvider.when('/student/doexam/:hash', { templateUrl: tmpl + 'doexam.html', controller: 'StudentExamController'});


            $routeProvider.otherwise({redirectTo: '/home'});
        }]);
}());