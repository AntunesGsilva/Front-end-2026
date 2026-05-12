<!DOCTYPE html>
<html>
<head>
    <title>CPF Simples</title>
    <style>
        .valido { color: green; font-weight: bold; }
        .invalido { color: red; font-weight: bold; }
    </style>
</head>
<body>
    <input type="text" id="cpf" placeholder="000.000.000-00">
    <button onclick="testar()">Validar</button>
    <div id="resultado"></div>

    <script>
        function testar() {
            const cpf = document.getElementById('cpf').value;
            const resultado = document.getElementById('resultado');
            
            if (validarCPF(cpf)) {
                resultado.innerHTML = '✅ VÁLIDO';
                resultado.className = 'valido';
            } else {
                resultado.innerHTML = '❌ INVÁLIDO';
                resultado.className = 'invalido';
            }
        }
    </script>
</body>
</html>
