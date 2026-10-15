<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Forgot Password</title>
    <style>
        .button {
            display: inline-block;
            padding: 10px 20px;
            margin-top: 20px;
            font-size: 16px;
            color: white;
            background-color: #007bff;
            text-decoration: none;
            border-radius: 5px;
        }

        .button:hover {
            background-color: #0056b3;
        }

        .container {
            font-family: Arial, sans-serif;
            padding: 20px;
        }

        .code {
            font-family: monospace;
            font-size: 16px;
            background-color: #eee;
            padding: 12px;
            border-radius: 5px;
            word-break: break-all;
        }

        .footer {
            margin-top: 30px;
            font-size: 12px;
            color: #666;
        }
    </style>
</head>
<body>
<div class="container">
    <h2>Hello, ${name}!</h2>
    <p>We received a request to reset your password. Enter this code on the "Reset Your Password" page:</p>
    <p class="code">${token}</p>
    <p>The code will expire in 24 hours.</p>
    <p>If you didn't request a password reset, you can ignore this email.</p>
    <div class="footer">
        <p>This message was sent by AITU Net. Do not reply to this email.</p>
    </div>
</div>
</body>
</html>
