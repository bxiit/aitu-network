<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        .code {
            font-family: monospace;
            font-size: 16px;
            background-color: #eee;
            padding: 12px;
            border-radius: 5px;
            word-break: break-all;
        }

        .container {
            font-family: Arial, sans-serif;
            padding: 20px;
            background-color: #f9f9f9;
            border-radius: 8px;
            width: 90%;
            max-width: 600px;
            margin: auto;
        }
    </style>
</head>
<body>
<div class="container">
    <h2>Hello, ${name}!</h2>
    <p>Thank you for registering. Your email confirmation code is:</p>
    <p class="code">${code}</p>
    <p>This code will expire in 24 hours.</p>
    <br>
    <p>Best regards,<br>The Team</p>
</div>
</body>
</html>
